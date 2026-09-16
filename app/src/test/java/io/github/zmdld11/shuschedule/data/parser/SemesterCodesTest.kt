package io.github.zmdld11.shuschedule.data.parser

import io.github.zmdld11.shuschedule.data.db.TermType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SemesterCodesTest {

    @Test
    fun guessByDate() {
        assertEquals(2026 to TermType.AUTUMN, SemesterCodes.guess(LocalDate.of(2026, 9, 16)))
        assertEquals(2026 to TermType.AUTUMN, SemesterCodes.guess(LocalDate.of(2026, 12, 31)))
        assertEquals(2025 to TermType.WINTER, SemesterCodes.guess(LocalDate.of(2026, 1, 10)))
        assertEquals(2025 to TermType.SPRING, SemesterCodes.guess(LocalDate.of(2026, 4, 1)))
        assertEquals(2025 to TermType.SUMMER, SemesterCodes.guess(LocalDate.of(2026, 7, 20)))
    }

    @Test
    fun xqmPrimaryCandidates() {
        assertEquals(3, SemesterCodes.xqmCandidates(TermType.AUTUMN).first())
        assertEquals(16, SemesterCodes.xqmCandidates(TermType.SPRING).first()) // 2026-09 真机导入实测：上大春=16
        assertEquals(32, SemesterCodes.xqmCandidates(TermType.SUMMER).first()) // jwxk .env 实测：改版后夏季=32
        // 冬季编码未知但必须给出可探测序列
        assertTrue(SemesterCodes.xqmCandidates(TermType.WINTER).isNotEmpty())
    }

    @Test
    fun defaultWeeks() {
        assertEquals(16, SemesterCodes.defaultTotalWeeks(TermType.AUTUMN))
        assertEquals(4, SemesterCodes.defaultTotalWeeks(TermType.WINTER))
    }

    private fun assertTrue(b: Boolean) = org.junit.Assert.assertTrue(b)
}
