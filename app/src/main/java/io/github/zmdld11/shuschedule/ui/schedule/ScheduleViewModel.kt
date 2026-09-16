package io.github.zmdld11.shuschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.db.Course
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import io.github.zmdld11.shuschedule.data.db.Semester
import io.github.zmdld11.shuschedule.data.db.TimeSlot
import io.github.zmdld11.shuschedule.data.parser.WeekTextParser
import io.github.zmdld11.shuschedule.data.repo.ScheduleRepository
import io.github.zmdld11.shuschedule.data.settings.SettingsStore
import io.github.zmdld11.shuschedule.widget.WidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ScheduleUiState(
    val semester: Semester? = null,
    val courses: List<CourseWithSessions> = emptyList(),
    val timeSlots: List<TimeSlot> = emptyList(),
) {
    val currentWeek: Int
        get() {
            val s = semester ?: return 1
            val days = LocalDate.now().toEpochDay() - s.startDateEpochDay
            return ((days / 7) + 1).toInt().coerceIn(1, s.totalWeeks)
        }

    /** 第 week 周第 weekday 天的日期 */
    fun dateOf(week: Int, weekday: Int): LocalDate? {
        val s = semester ?: return null
        return LocalDate.ofEpochDay(s.startDateEpochDay + (week - 1) * 7L + (weekday - 1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val widgetUpdater: WidgetUpdater,
    settings: SettingsStore,
) : ViewModel() {

    /** 周视图是否置灰显示非本周课程 */
    val showOffWeek: StateFlow<Boolean> =
        settings.showOffWeek.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 周视图是否显示周六周日（默认只显示工作日） */
    val showWeekend: StateFlow<Boolean> =
        settings.showWeekend.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 节次时间列是否显示下课时间 */
    val showSlotEnd: StateFlow<Boolean> =
        settings.showSlotEnd.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _semesterFlow = repository.observeActiveSemester()

    /** 全部学期（顶栏快捷切换用） */
    val semesters: StateFlow<List<Semester>> =
        repository.observeSemesters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<ScheduleUiState> = _semesterFlow
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(ScheduleUiState())
            } else {
                kotlinx.coroutines.flow.combine(
                    repository.observeCourses(semester.id),
                    repository.observeTimeSlots(),
                ) { courses, slots ->
                    ScheduleUiState(semester, courses, slots)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    val selectedWeek = MutableStateFlow<Int?>(null) // null = 跟随当前周

    private val _detailCourse = MutableStateFlow<CourseWithSessions?>(null)
    val detailCourse: StateFlow<CourseWithSessions?> = _detailCourse.asStateFlow()


    fun selectWeek(week: Int?) {
        selectedWeek.value = week?.let { it.coerceAtLeast(1) }
    }

    /** 顶栏快捷切换学期：整个 App 与小组件跟随激活学期 */
    fun activateSemester(id: Long) {
        viewModelScope.launch {
            repository.activateSemester(id)
            widgetUpdater.pushAll()
            selectedWeek.value = null
        }
    }

    fun showDetail(course: CourseWithSessions?) {
        _detailCourse.value = course
    }

    // ---------- 课程手动编辑 ----------

    /** 编辑目标：session=null 表示给该课新增时段；course.id=0 表示新建自定义课程 */
    data class SessionEditTarget(val course: Course, val session: CourseSession?)

    private val _editorTarget = MutableStateFlow<SessionEditTarget?>(null)
    val editorTarget: StateFlow<SessionEditTarget?> = _editorTarget.asStateFlow()

    fun openSessionEditor(course: Course, session: CourseSession?) {
        _detailCourse.value = null
        _editorTarget.value = SessionEditTarget(course, session)
    }

    fun openNewCourseEditor() {
        val semester = state.value.semester ?: return
        _editorTarget.value = SessionEditTarget(
            course = Course(
                semesterId = semester.id,
                name = "",
                courseCode = "自定义",
                className = "",
                classId = "",
                credit = "",
            ),
            session = null,
        )
    }

    fun closeEditor() {
        _editorTarget.value = null
    }

    fun saveSessionEdit(
        name: String,
        weekday: Int,
        startNode: Int,
        endNode: Int,
        weeksText: String,
        room: String,
        teacher: String,
        campus: String,
    ) {
        val target = _editorTarget.value ?: return
        val weeksMask = WeekTextParser.parseMask(weeksText)
        viewModelScope.launch {
            runCatching {
                when {
                    target.session == null && target.course.id == 0L ->
                        repository.addCustomCourse(
                            semesterId = target.course.semesterId,
                            name = name,
                            session = CourseSession(
                                courseId = 0,
                                weekday = weekday,
                                startNode = startNode,
                                endNode = endNode,
                                weeksMask = weeksMask,
                                room = room,
                                teacher = teacher,
                                campus = campus,
                            ),
                        )

                    target.session == null ->
                        repository.addSession(
                            course = target.course.copy(name = name),
                            session = CourseSession(
                                courseId = target.course.id,
                                weekday = weekday,
                                startNode = startNode,
                                endNode = endNode,
                                weeksMask = weeksMask,
                                room = room,
                                teacher = teacher,
                                campus = campus,
                            ),
                        )

                    else ->
                        repository.saveSessionEdit(
                            course = target.course.copy(name = name),
                            session = target.session.copy(
                                weekday = weekday,
                                startNode = startNode,
                                endNode = endNode,
                                weeksMask = weeksMask,
                                room = room,
                                teacher = teacher,
                                campus = campus,
                            ),
                        )
                }
            }.onSuccess {
                widgetUpdater.pushAll()
                _editorTarget.value = null
            }
        }
    }

    /** 删除编辑中的时段（课程因此无时段则整门课删除） */
    fun deleteEditingSession() {
        val target = _editorTarget.value ?: return
        val session = target.session ?: return
        viewModelScope.launch {
            repository.deleteSessionAndOrphanCourse(session)
            widgetUpdater.pushAll()
            _editorTarget.value = null
        }
    }

    fun deleteCourse(courseId: Long) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
            widgetUpdater.pushAll()
            _detailCourse.value = null
        }
    }

    /** 某周某天的课程块：CourseSession + 对应 Course；inWeek=该周是否上这节课 */
    data class DayBlock(
        val course: CourseWithSessions,
        val session: CourseSession,
        val inWeek: Boolean,
    )

    fun blocksFor(week: Int, weekday: Int, includeOffWeek: Boolean = false): List<DayBlock> =
        filterBlocks(state.value.courses, week, weekday, includeOffWeek)
}

/** 纯函数便于单测：按周/星期过滤排课块 */
fun filterBlocks(
    courses: List<CourseWithSessions>,
    week: Int,
    weekday: Int,
    includeOffWeek: Boolean,
): List<ScheduleViewModel.DayBlock> {
    val all = courses
        .flatMap { c -> c.sessions.map { ScheduleViewModel.DayBlock(c, it, it.hasWeek(week)) } }
        .filter { it.session.weekday == weekday }
    return (if (includeOffWeek) all else all.filter { it.inWeek })
        .sortedWith(compareBy({ it.session.startNode }, { it.course.course.name }))
}

/** 周视图课程色板（8 色，colorIndex 取模） */
object CoursePalette {
    data class Pair(val container: Long, val onContainer: Long)

    val colors = listOf(
        Pair(0xFFD7E8FF, 0xFF0D3B7A), // 蓝
        Pair(0xFFFFE3EE, 0xFF8A2450), // 粉
        Pair(0xFFE2F5E1, 0xFF205C24), // 绿
        Pair(0xFFFFF0CC, 0xFF7A5410), // 黄
        Pair(0xFFEAE1FF, 0xFF4A2E8F), // 紫
        Pair(0xFFFFE4D6, 0xFF843B12), // 橙
        Pair(0xFFDDF4F5, 0xFF0F5B60), // 青
        Pair(0xFFF6E0E8, 0xFF75364E), // 玫瑰
    )

    fun container(index: Int) = androidx.compose.ui.graphics.Color(colors[index % colors.size].container)
    fun onContainer(index: Int) = androidx.compose.ui.graphics.Color(colors[index % colors.size].onContainer)
}
