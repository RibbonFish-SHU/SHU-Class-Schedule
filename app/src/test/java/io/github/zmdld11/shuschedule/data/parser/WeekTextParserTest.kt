package io.github.zmdld11.shuschedule.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekTextParserTest {

    private fun weeks(s: String?) = WeekTextParser.parseWeeks(s).sorted()

    @Test
    fun rangeText() {
        assertEquals((1..8).toList(), weeks("1-8周"))
        assertEquals((9..16).toList(), weeks("9-16周"))
    }

    @Test
    fun oddEvenSuffix() {
        assertEquals((2..16 step 2).toList(), weeks("2-16周(双)"))
        assertEquals(listOf(9, 11, 13, 15), weeks("9-15周(单)"))
        assertEquals((2..10 step 2).toList(), weeks("1-10周(偶)"))
        assertEquals(listOf(1, 3, 5), weeks("1-5周(奇)"))
    }

    @Test
    fun commaList() {
        assertEquals(listOf(1, 5, 9, 13), weeks("1,5,9,13周"))
        assertEquals(listOf(1, 2, 5), weeks("1,2,5周"))
    }

    @Test
    fun fullWidthParentheses() {
        assertEquals((2..16 step 2).toList(), weeks("2-16周（双）"))
    }

    @Test
    fun blankFallsBackToFullSemester() {
        assertEquals((1..16).toList(), weeks(null))
        assertEquals((1..16).toList(), weeks(""))
        assertEquals((1..16).toList(), weeks("周"))
    }

    @Test
    fun garbageFallsBackToFullSemester() {
        assertEquals((1..16).toList(), weeks("待定"))
    }

    @Test
    fun maskRoundtrip() {
        val mask = WeekTextParser.parseMask("2-16周(双)")
        val back = io.github.zmdld11.shuschedule.data.db.CourseSession.weeksOf(mask).sorted()
        assertEquals((2..16 step 2).toList(), back)
    }

    @Test
    fun diPrefixSingleWeek() {
        assertEquals(setOf(13), WeekTextParser.parseWeeks("第13周"))
        assertEquals(setOf(1), WeekTextParser.parseWeeks("第1周"))
    }

    @Test
    fun diPrefixRange() {
        assertEquals((9..16).toSet(), WeekTextParser.parseWeeks("第9-16周"))
    }
}
