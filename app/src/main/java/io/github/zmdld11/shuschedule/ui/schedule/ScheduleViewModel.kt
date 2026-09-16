package io.github.zmdld11.shuschedule.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import io.github.zmdld11.shuschedule.data.db.Semester
import io.github.zmdld11.shuschedule.data.db.TimeSlot
import io.github.zmdld11.shuschedule.data.repo.ScheduleRepository
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
) : ViewModel() {

    private val _semesterFlow = repository.observeActiveSemester()

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

    fun showDetail(course: CourseWithSessions?) {
        _detailCourse.value = course
    }

    /** 某周某天的课程块：CourseSession + 对应 Course */
    data class DayBlock(
        val course: CourseWithSessions,
        val session: CourseSession,
    )

    fun blocksFor(week: Int, weekday: Int): List<DayBlock> =
        state.value.courses
            .flatMap { c -> c.sessions.map { DayBlock(c, it) } }
            .filter { it.session.weekday == weekday && it.session.hasWeek(week) }
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
