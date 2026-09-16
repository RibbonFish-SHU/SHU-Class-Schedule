package io.github.zmdld11.shuschedule.data.parser

import io.github.zmdld11.shuschedule.data.db.TermType
import java.time.LocalDate

/**
 * 正方课表接口的学期参数。
 *
 * xnm = 学年起始年（如 2026-2027 学年 → xnm=2026）。
 * xqm 常规正方编码：3=第一学期(秋)、12=第二学期(春)、16=第三学期(夏)。
 * 上大为四季学期制且教务改版过编码（SHU-jwxk 选课接口实测出现过 32 这类非常规值），
 * 故 xqm 以"候选值序列 + 运行时探测（请求后看 kbList 是否非空）"为准，首个真机导入时校准。
 */
object SemesterCodes {

    fun xqmCandidates(term: TermType): List<Int> = when (term) {
        TermType.AUTUMN -> listOf(3)
        TermType.WINTER -> listOf(45, 29, 32) // 上大冬季短学期编码未知，待 #3 实测校准
        TermType.SPRING -> listOf(12)
        TermType.SUMMER -> listOf(16)
    }

    /** 探测顺序：先本学期，再相邻学期（导入时用户已选定学年+学期，这里只排 xqm 候选） */
    fun xqmProbeOrder(year: Int, term: TermType): List<Pair<Int, Int>> =
        xqmCandidates(term).map { xqm -> year to xqm }

    /** 按日期猜（学年, 学期）：9-12月=当年秋；1月=前一年冬；2-6月=前一年春；7-8月=前一年夏 */
    fun guess(date: LocalDate = LocalDate.now()): Pair<Int, TermType> {
        val y = date.year
        val m = date.monthValue
        return when (m) {
            in 9..12 -> y to TermType.AUTUMN
            1 -> (y - 1) to TermType.WINTER
            in 2..6 -> (y - 1) to TermType.SPRING
            else -> (y - 1) to TermType.SUMMER // 7-8 月
        }
    }

    /** 四季学期默认总周数：秋/春 长学期 16 周（实际含考试周约 20，先按教学周），冬/夏 短学期 4 周 */
    fun defaultTotalWeeks(term: TermType): Int = when (term) {
        TermType.AUTUMN, TermType.SPRING -> 16
        TermType.WINTER, TermType.SUMMER -> 4
    }
}
