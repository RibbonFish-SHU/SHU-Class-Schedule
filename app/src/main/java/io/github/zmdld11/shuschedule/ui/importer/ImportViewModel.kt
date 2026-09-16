package io.github.zmdld11.shuschedule.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.db.TermType
import io.github.zmdld11.shuschedule.data.jwxk.JwxkSpec
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

    /** 学年（起始年）与学期选择 */
    val selectedYear = MutableStateFlow(SemesterCodes.guess().first)
    val selectedTerm = MutableStateFlow(SemesterCodes.guess().second)

    private var pendingPreview: ImportState.Preview? = null

    init {
        _loginProbeScript.value = JwxkSpec.loginProbeScript(BRIDGE_NAME)
    }

    /** 登录探测回调（来自 WebView 桥） */
    fun onLoginProbe(loggedIn: Boolean) {
        if (_state.value is ImportState.WaitingLogin && loggedIn) {
            _state.value = ImportState.Ready
        } else if (_state.value is ImportState.Ready && !loggedIn) {
            _state.value = ImportState.WaitingLogin
        }
    }

    fun startFetch() {
        if (_state.value is ImportState.Fetching) return
        _state.value = ImportState.Fetching
        _fetchScript.value = JwxkSpec.fetchScheduleScript(
            bridge = BRIDGE_NAME,
            xnm = selectedYear.value,
            xqmCandidates = SemesterCodes.xqmCandidates(selectedTerm.value),
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
        _state.value = ImportState.Preview(
            year = selectedYear.value,
            term = selectedTerm.value,
            xqmUsed = xqm,
            courses = courses,
            suggestedStart = defaultSemesterStart(selectedYear.value, selectedTerm.value),
            emptySemester = empty || courses.isEmpty(),
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
