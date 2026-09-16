package io.github.zmdld11.shuschedule.widget

import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.DayOverride
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import io.github.zmdld11.shuschedule.data.db.Semester
import io.github.zmdld11.shuschedule.data.db.TimeSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 小组件用的"今日课程"快照 */
data class TodayItem(
    val name: String,
    val startNode: Int,
    val endNode: Int,
    val startTime: String,
    val endTime: String,
    val place: String,   // 校区·教室
    val teacher: String,
    /** 该节正在上课中（当前时间落在其起止内） */
    val inProgress: Boolean = false,
)

data class TodayData(
    val semesterName: String,
    val week: Int,
    val weekLabel: String,   // "第3周 · 周三 9/16"
    val items: List<TodayItem>,
    /** 未结束的课程（已下课的剔除，列表小组件往上顶显示） */
    val upcomingItems: List<TodayItem>,
    /** items 中"接下来"那节的下标（正在上=该节；已全结束=null）；无课=null */
    val nextIndex: Int?,
    val inClass: Boolean,
)

object TodaySchedule {

    private val DAY_CHARS = listOf("一", "二", "三", "四", "五", "六", "日")

    fun build(
        semester: Semester?,
        courses: List<CourseWithSessions>,
        timeSlots: List<TimeSlot>,
        overrides: List<DayOverride> = emptyList(),
        now: LocalDate = LocalDate.now(),
        clock: LocalTime = LocalTime.now(),
    ): TodayData {
        if (semester == null) {
            return TodayData("", 0, "未导入课表", emptyList(), emptyList(), null, inClass = false)
        }
        val days = now.toEpochDay() - semester.startDateEpochDay
        val week = ((days / 7) + 1).toInt().coerceIn(1, semester.totalWeeks)
        val weekday = now.dayOfWeek.value // 1=周一
        val inSemester = days >= 0 && week <= semester.totalWeeks

        // 调休覆盖：放假=当天无课；调休补课=按来源星期取课
        val todayOverride = overrides.firstOrNull { it.week == week && it.weekday == weekday }
        val isHoliday = inSemester && todayOverride?.mode == DayOverride.MODE_HOLIDAY
        val effectiveWeekday = if (inSemester && todayOverride?.mode == DayOverride.MODE_SUBSTITUTE && todayOverride.substituteWeekday in 1..7) {
            todayOverride.substituteWeekday
        } else {
            weekday
        }

        val slots = timeSlots.associateBy { it.node }
        val items = if (isHoliday) {
            emptyList()
        } else {
            courses
                .flatMap { c -> c.sessions.filter { it.weekday == effectiveWeekday && (inSemester && it.hasWeek(week)) }.map { c to it } }
                .sortedWith(compareBy({ (_, s) -> s.startNode }, { (c, _) -> c.course.name }))
                .map { (c, s) ->
                    TodayItem(
                        name = c.course.name,
                        startNode = s.startNode,
                        endNode = s.endNode,
                        startTime = slots[s.startNode]?.startTime ?: "",
                        endTime = slots[s.endNode]?.endTime ?: "",
                        place = listOf(s.campus.takeIf { it.isNotBlank() }, s.room.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString("·"),
                        teacher = s.teacher,
                    )
                }
        }

        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        val nowStr = clock.format(hhmm)
        var nextIndex: Int? = null
        var inClass = false
        if (!isHoliday) {
            for ((i, item) in items.withIndex()) {
                if (item.endTime.isBlank()) continue
                when {
                    nowStr <= item.startTime -> { nextIndex = i; inClass = false; break }
                    nowStr <= item.endTime -> { nextIndex = i; inClass = true; break }
                }
            }
        }

        // 时间感知：正在上的那节打标；已下课的剔除（时间未知的不剔除）
        val marked = items.mapIndexed { i, item ->
            item.copy(inProgress = i == nextIndex && inClass)
        }
        val upcoming = marked.filter { it.endTime.isBlank() || nowStr <= it.endTime }

        val label = buildString {
            append("第${week}周 · 周${DAY_CHARS[weekday - 1]} ${now.monthValue}/${now.dayOfMonth}")
            if (isHoliday) append(" · 假期")
            if (todayOverride?.mode == DayOverride.MODE_SUBSTITUTE) append(" · 按周${DAY_CHARS[effectiveWeekday - 1]}上")
        }

        return TodayData(
            semesterName = semester.displayName,
            week = week,
            weekLabel = label,
            items = marked,
            upcomingItems = upcoming,
            nextIndex = nextIndex,
            inClass = inClass,
        )
    }
}
