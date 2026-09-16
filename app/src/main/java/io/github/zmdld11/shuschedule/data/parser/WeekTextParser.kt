package io.github.zmdld11.shuschedule.data.parser

import io.github.zmdld11.shuschedule.data.db.CourseSession

/**
 * 正方"周次"文本解析。逻辑移植自 SHU-jwxk-assistant app.py#parse_weeks（已长期验证）。
 *
 * 支持格式：`1-8周`、`9-16周`、`2-16周(双)`、`1,5,9,13周`、`9-15周(单)`、`(奇)`/`(偶)`、
 * **`第13周`/`第9-16周`（调课记录的写法，2026-09 真机 kbList 实测）**；
 * 全角括号归一化为半角后处理；空白/解析失败时回退整学期默认周次。
 */
object WeekTextParser {

    private val RANGE = Regex("""(\d+)\s*[-–—]\s*(\d+)""")
    private val WEEK_LIMIT = 1..CourseSession.MAX_WEEKS

    fun parseWeeks(text: String?, defaultWeeks: IntRange = 1..16): Set<Int> {
        if (text.isNullOrBlank()) return defaultWeeks.toSet()
        var s = text.replace("周", "").replace("第", "").trim()
        if (s.isEmpty()) return defaultWeeks.toSet()
        s = s.replace("（", "(").replace("）", ")")
        val odd = s.contains("(单)") || s.contains("(奇)")
        val even = s.contains("(双)") || s.contains("(偶)")
        s = s.replace(Regex("""\(.*?\)"""), "").trim()

        val weeks = mutableSetOf<Int>()
        for (part in s.split(",", "，")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val m = RANGE.find(p)
            if (m != null) {
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                if (a in WEEK_LIMIT && b in WEEK_LIMIT && a <= b) weeks.addAll(a..b)
            } else {
                p.toIntOrNull()?.takeIf { it in WEEK_LIMIT }?.let { weeks.add(it) }
            }
        }
        if (weeks.isEmpty()) return defaultWeeks.toSet()
        return when {
            odd -> weeks.filterTo(mutableSetOf()) { it % 2 == 1 }
            even -> weeks.filterTo(mutableSetOf()) { it % 2 == 0 }
            else -> weeks
        }
    }

    fun parseMask(text: String?, defaultWeeks: IntRange = 1..16): Int =
        CourseSession.maskOf(parseWeeks(text, defaultWeeks))
}
