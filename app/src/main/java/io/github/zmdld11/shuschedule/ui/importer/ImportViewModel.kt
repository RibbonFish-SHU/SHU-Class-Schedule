package io.github.zmdld11.shuschedule.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.db.TermType
import io.github.zmdld11.shuschedule.data.jwxk.JwxkSpec
import io.github.zmdld11.shuschedule.data.jwxk.SemesterChoices
import io.github.zmdld11.shuschedule.data.jwxk.SemesterOption
import io.github.zmdld11.shuschedule.data.jwxk.SemesterOptionsParser
import io.github.zmdld11.shuschedule.data.parser.ParsedCourse
import io.github.zmdld11.shuschedule.data.parser.SemesterCodes
import io.github.zmdld11.shuschedule.data.parser.ZfKbListParser
import io.github.zmdld11.shuschedule.data.repo.ScheduleRepository
import io.github.zmdld11.shuschedule.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

sealed interface ImportState {
    /** WebView 展示中，等待用户登录 */
    data object WaitingLogin : ImportState

    /** 已探测到登录态，可以抓取 */
    data object Ready : ImportState

    data object Fetching : ImportState

    data class Preview(
        val year: Int,
        val term: TermType,
        val xqmUsed: Int,
        val courses: List<ParsedCourse>,
        val suggestedStart: LocalDate,
        val emptySemester: Boolean,
        /** 教务返回的原始 payload JSON（「复制原始数据」与问题定位用） */
        val rawJson: String = "",
    ) : ImportState

    data class Done(val importedCourses: Int, val semesterName: String) : ImportState

    data class Error(val message: String) : ImportState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<ImportState>(ImportState.WaitingLogin)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _loginProbeScript = MutableStateFlow<String?>(null)
    /** WebView 每页加载完成后注入一次的探测脚本（幂等） */
    val loginProbeScript: StateFlow<String?> = _loginProbeScript.asStateFlow()

    /** 一次性抓取指令：Screen 观察到非空即注入 WebView 并清空 */
    private val _fetchScript = MutableStateFlow<String?>(null)
    val fetchScript: StateFlow<String?> = _fetchScript.asStateFlow()

    /** 登录成功后导航到课表查询页的一次性指令（去那读真实学期编码） */
    private val _loadUrl = MutableStateFlow<String?>(null)
    val loadUrl: StateFlow<String?> = _loadUrl.asStateFlow()

    /** 教务页采集到的真实学年/学期选项；null = 还没读到 */
    private val _semesterChoices = MutableStateFlow<SemesterChoices?>(null)
    val semesterChoices: StateFlow<SemesterChoices?> = _semesterChoices.asStateFlow()

    /** 采集超时放弃（回退硬编码候选） */
    private val _harvestFailed = MutableStateFlow(false)
    val harvestFailed: StateFlow<Boolean> = _harvestFailed.asStateFlow()

    /** 学年（起始年）与学期选择（静态兜底用 TermType；动态模式用教务选项） */
    val selectedYear = MutableStateFlow(SemesterCodes.guess().first)
    val selectedTerm = MutableStateFlow(SemesterCodes.guess().second)
    val selectedTermOption = MutableStateFlow<SemesterOption?>(null)

    init {
        _loginProbeScript.value = JwxkSpec.loginProbeScript(BRIDGE_NAME)
    }

    /** 登录探测回调（来自 WebView 桥） */
    fun onLoginProbe(loggedIn: Boolean) {
        if (_state.value is ImportState.WaitingLogin && loggedIn) {
            _state.value = ImportState.Ready
            // 登录态已建立：去课表查询页读教务自己的学年/学期下拉（真实 xnm/xqm）
            _loadUrl.value = JwxkSpec.INDEX_URL
        } else if (_state.value is ImportState.Ready && !loggedIn) {
            _state.value = ImportState.WaitingLogin
        }
    }

    fun consumeLoadUrl() {
        _loadUrl.value = null
    }

    /** 学期下拉采集回调；ok=false 表示页面还没渲染好，等下一轮 */
    fun onSemesterJson(text: String) {
        if (_semesterChoices.value != null) return
        val choices = SemesterOptionsParser.parse(text) ?: return
        _semesterChoices.value = choices
        preselect(choices)
    }

    /** 采集轮询超时：回退静态编码选择 */
    fun onHarvestGaveUp() {
        if (_semesterChoices.value == null) _harvestFailed.value = true
    }

    private fun preselect(choices: SemesterChoices) {
        val (guessYear, guessTerm) = SemesterCodes.guess()
        val year = choices.years.firstOrNull { it.selected }
            ?: choices.years.firstOrNull { it.value == guessYear }
            ?: choices.years.maxByOrNull { it.value }
        year?.let { selectedYear.value = it.value }

        val term = choices.terms.firstOrNull { it.selected }
            ?: choices.terms.firstOrNull {
                SemesterOptionsParser.termTypeOf(it) == guessTerm
            }
            ?: choices.terms.firstOrNull()
        selectedTermOption.value = term
        term?.let { SemesterOptionsParser.termTypeOf(it)?.let { t -> selectedTerm.value = t } }
    }

    fun selectYearOption(option: SemesterOption) {
        selectedYear.value = option.value
    }

    fun selectTermOption(option: SemesterOption) {
        selectedTermOption.value = option
        SemesterOptionsParser.termTypeOf(option)?.let { selectedTerm.value = it }
    }

    fun startFetch() {
        if (_state.value is ImportState.Fetching) return
        _state.value = ImportState.Fetching
        val code = selectedTermOption.value?.value
        _fetchScript.value = JwxkSpec.fetchScheduleScript(
            bridge = BRIDGE_NAME,
            xnm = selectedYear.value,
            // 有教务真实编码就精确请求；没有才回退候选探测
            xqmCandidates = code?.let { listOf(it) } ?: SemesterCodes.xqmCandidates(selectedTerm.value),
        )
    }

    fun consumeFetchScript() {
        _fetchScript.value = null
    }

    /** 抓取结果回调（来自 WebView 桥，envelope: {ok, xqm?, payload?, empty?, error?}） */
    fun onScheduleJson(envelopeText: String) {
        val envelope = runCatching { json.parseToJsonElement(envelopeText).jsonObject }.getOrNull()
        if (envelope == null) {
            _state.value = ImportState.Error("教务返回了无法解析的数据")
            return
        }
        if (envelope["ok"]?.jsonPrimitive?.content != "true") {
            val err = envelope["error"]?.jsonPrimitive?.content
            _state.value = ImportState.Error(
                when (err) {
                    "login" -> "登录态失效：请在上方网页重新登录后再点「开始抓取」"
                    "network" -> "网络请求失败，请检查网络后重试"
                    else -> "导入失败：$err"
                }
            )
            return
        }
        val xqm = envelope["xqm"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
        val kbList = envelope["payload"]?.jsonObject?.get("kbList")?.jsonArray
        val empty = envelope["empty"]?.jsonPrimitive?.content == "true"
        if (kbList == null) {
            _state.value = ImportState.Error("教务返回数据缺少课表字段（可能教务改版，欢迎提 issue）")
            return
        }
        val courses = ZfKbListParser.parse(
            JsonObject(mapOf("kbList" to kbList)).let { json.encodeToString(JsonElement.serializer(), it) }
        )
        val rawJson = envelope["payload"]?.toString() ?: ""
        // 分块打进 logcat（tag=ShuImport），供真机/模拟器问题定位时直接 adb 抓取
        rawJson.chunked(3000).forEachIndexed { i, chunk ->
            android.util.Log.d("ShuImport", "raw[$i]=$chunk")
        }
        _state.value = ImportState.Preview(
            year = selectedYear.value,
            term = selectedTerm.value,
            xqmUsed = xqm,
            courses = courses,
            suggestedStart = defaultSemesterStart(selectedYear.value, selectedTerm.value),
            emptySemester = empty || courses.isEmpty(),
            rawJson = rawJson,
        )
    }

    /** 预览页确认导入（开学日期可改） */
    fun confirmImport(startDate: LocalDate) {
        val preview = _state.value as? ImportState.Preview ?: return
        viewModelScope.launch {
            runCatching {
                repository.importParsed(
                    year = preview.year,
                    term = preview.term,
                    startDateEpochDay = startDate.toEpochDay(),
                    totalWeeks = SemesterCodes.defaultTotalWeeks(preview.term),
                    parsed = preview.courses,
                )
            }.onSuccess {
                widgetUpdater.pushAll()
                _state.value = ImportState.Done(
                    importedCourses = preview.courses.size,
                    semesterName = "${preview.year}-${preview.year + 1}学年${preview.term.label}",
                )
            }.onFailure { e ->
                _state.value = ImportState.Error("写入本地数据库失败：${e.message}")
            }
        }
    }

    /** 从预览/错误退回抓取前状态 */
    fun backToReady() {
        _state.value = ImportState.Ready
    }

    companion object {
        const val BRIDGE_NAME = "ShuBridge"

        /** 学期第一周周一的默认建议值（用户可在预览页修改） */
        fun defaultSemesterStart(year: Int, term: TermType): LocalDate = when (term) {
            TermType.AUTUMN -> LocalDate.of(year, 9, 8).nextOrSameMonday()
            TermType.WINTER -> LocalDate.of(year + 1, 1, 5).nextOrSameMonday()
            TermType.SPRING -> LocalDate.of(year + 1, 2, 24).nextOrSameMonday()
            TermType.SUMMER -> LocalDate.of(year + 1, 7, 6).nextOrSameMonday()
        }

        private fun LocalDate.nextOrSameMonday(): LocalDate =
            if (dayOfWeek == DayOfWeek.MONDAY) this else plusDays(((8 - dayOfWeek.value) % 7).toLong())
    }
}
