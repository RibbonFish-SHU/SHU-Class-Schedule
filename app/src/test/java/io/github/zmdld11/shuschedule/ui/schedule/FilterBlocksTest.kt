package io.github.zmdld11.shuschedule.ui.schedule

import io.github.zmdld11.shuschedule.data.db.Course
import io.github.zmdld11.shuschedule.data.db.CourseSession
import io.github.zmdld11.shuschedule.data.db.CourseWithSessions
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterBlocksTest {

    private fun course(name: String, vararg sessions: CourseSession) =
        CourseWithSessions(
            course = Course(semesterId = 1, name = name, courseCode = "", className = "", classId = "", credit = ""),
            sessions = sessions.toList(),
        )

    private fun session(weekday: Int, start: Int, end: Int, weeks: Int) =
        CourseSession(courseId = 1, weekday = weekday, startNode = start, endNode = end, weeksMask = weeks, room = "", teacher = "")

    private val courses = listOf(
        course(
            "数据结构",
            session(1, 1, 2, CourseSession.maskOf((1..8).toList())),      // 周一 1-2, 1-8周
            session(3, 3, 4, CourseSession.maskOf((2..16 step 2).toList())), // 周三 3-4, 双周
        ),
        course(
            "大学英语",
            session(5, 10, 10, CourseSession.maskOf(listOf(1, 5, 9, 13))), // 周五 10, 1/5/9/13
        ),
    )

    @Test
    fun hidesOffWeekByDefault() {
        // 第 2 周：周一有课、周五无课
        val mon = filterBlocks(courses, week = 2, weekday = 1, includeOffWeek = false)
        assertEquals(1, mon.size)
        assertEquals(true, mon.single().inWeek)

        val fri = filterBlocks(courses, week = 2, weekday = 5, includeOffWeek = false)
        assertEquals(0, fri.size)
    }

    @Test
    fun showsOffWeekDimmedWhenEnabled() {
        // 第 2 周周五：大学英语不在本周，开启后出现且 inWeek=false
        val fri = filterBlocks(courses, week = 2, weekday = 5, includeOffWeek = true)
        assertEquals(1, fri.size)
        assertEquals("大学英语", fri.single().course.course.name)
        assertEquals(false, fri.single().inWeek)
    }

    @Test
    fun oddWeekExcludesEvenWeekSession() {
        // 第 1 周周三：双周课不在本周
        val wed = filterBlocks(courses, week = 1, weekday = 3, includeOffWeek = true)
        assertEquals(1, wed.size)
        assertEquals(false, wed.single().inWeek)

        val wedHidden = filterBlocks(courses, week = 1, weekday = 3, includeOffWeek = false)
        assertEquals(0, wedHidden.size)
    }

    @Test
    fun sortedByStartNode() {
        val multi = listOf(
            course("B课", session(1, 3, 4, CourseSession.maskOf((1..16).toList()))),
            course("A课", session(1, 1, 2, CourseSession.maskOf((1..16).toList()))),
        )
        val blocks = filterBlocks(multi, week = 1, weekday = 1, includeOffWeek = false)
        assertEquals(listOf("A课", "B课"), blocks.map { it.course.course.name })
    }

    @Test
    fun occupiedSlotSuppressesOffWeekBlocks() {
        // 同槽位：张瑞第1周（过去）+ 钱权2-8周 + 算法9-16周，浏览第3周 → 只显示本周的钱权
        val courses = listOf(
            course("计算机网络", session(4, 3, 4, CourseSession.maskOf(listOf(1))), session(4, 3, 4, CourseSession.maskOf((2..8).toList()))),
            course("算法设计与分析", session(4, 3, 4, CourseSession.maskOf((9..16).toList()))),
        )
        val thu = filterBlocks(courses, week = 3, weekday = 4, includeOffWeek = true)
        assertEquals(1, thu.size)
        assertEquals("计算机网络", thu.single().course.course.name)
        assertEquals(true, thu.single().inWeek)
    }

    @Test
    fun emptySlotShowsNearestFutureOnly() {
        // 周三9-10 算法 9-15周(单)，浏览双数第10周 → 无本周课，最近未来是第11周 → 置灰显示
        val courses = listOf(course("算法", session(3, 9, 10, CourseSession.maskOf((9..15 step 2).toList()))))
        val wed = filterBlocks(courses, week = 10, weekday = 3, includeOffWeek = true)
        assertEquals(1, wed.size)
        assertEquals(false, wed.single().inWeek)

        // 同槽位另一门只在第4周 → 浏览第2周时应显示更近的第4周那门，而非第9周
        val two = listOf(
            course("早课", session(3, 9, 10, CourseSession.maskOf(listOf(4)))),
            course("晚课", session(3, 9, 10, CourseSession.maskOf((9..16).toList()))),
        )
        val nearest = filterBlocks(two, week = 2, weekday = 3, includeOffWeek = true)
        assertEquals(1, nearest.size)
        assertEquals("早课", nearest.single().course.course.name)
    }

    @Test
    fun noFutureOccurrenceShowsNothing() {
        // 只在前2周有课，浏览第5周开非本周 → 不显示
        val courses = listOf(course("完结课", session(1, 1, 2, CourseSession.maskOf((1..2).toList()))))
        val mon = filterBlocks(courses, week = 5, weekday = 1, includeOffWeek = true)
        assertEquals(0, mon.size)
    }
}
