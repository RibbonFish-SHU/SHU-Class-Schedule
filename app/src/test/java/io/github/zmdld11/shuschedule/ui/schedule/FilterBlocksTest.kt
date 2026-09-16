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
}
