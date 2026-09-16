package io.github.zmdld11.shuschedule.data.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** kbList 单条排课记录解析结果（对应一次固定时间段的上课安排） */
data class ParsedSession(
    val weekday: Int,
    val startNode: Int,
    val endNode: Int,
    val weeksMask: Int,
    val weekText: String,
    val room: String,
    val teacher: String,
    val campus: String = "",
)

/** 一门课（按 kch + jxb_id 聚合）及其全部排课记录 */
data class ParsedCourse(
    val name: String,
    val courseCode: String,
    val className: String,
    val classId: String,
    val credit: String,
    val sessions: List<ParsedSession>,
)

/**
 * 正方教务 `xskbcx_cxXsgrkb.html?gnmkdm=N2151` 返回 JSON 的 kbList 解析器。
 * 字段口径与 SHU-jwxk-assistant app.py#api_schedule 的消费逻辑一致。
 *
 * 要点：
 * - 一条 kbList 记录 = 一个排课单元（weekday/节次/周次固定），同一门课多时段会拆成多条
 * - 节次取 `jcs`（如 "3-4"），缺失时回退从 `jc` 文本抽数字
 * - 同一 (kch, jxb_id) 的记录聚合为一门课；完全相同的排课记录去重
 * - 调课消解：同学期同课同星期同节次且周次有交集的两条记录（常规 vs 调课，
 *   如教师临时调换）默认保留周次覆盖更长的那条（原教师/常规记录）；
 *   周次不交集的正常分段（前8周/后8周换教室等）不受影响，特殊情况可导入后手动编辑
 */
object ZfKbListParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): List<ParsedCourse> {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return emptyList()
        val kbList = root["kbList"]?.jsonArray ?: return emptyList()

        data class Builder(
            val name: String,
            val courseCode: String,
            val className: String,
            val classId: String,
            val credit: String,
            val sessions: MutableSet<ParsedSession> = linkedSetOf(),
        )

        val byClassKey = linkedMapOf<String, Builder>()
        for (element in kbList) {
            val o = runCatching { element.jsonObject }.getOrNull() ?: continue
            val name = o.str("kcmc") ?: continue
            val courseCode = o.str("kch").ifNullOr { o.str("kch_id") } ?: ""
            val classId = o.str("jxb_id") ?: ""
            val weekday = o.str("xqj")?.toIntOrNull() ?: continue
            if (weekday !in 1..7) continue

            val (startNode, endNode) = parseNodes(o.str("jcs"), o.str("jc")) ?: continue
            val zcd = o.str("zcd").orEmpty()
            val session = ParsedSession(
                weekday = weekday,
                startNode = startNode,
                endNode = endNode,
                weeksMask = WeekTextParser.parseMask(zcd),
                weekText = zcd,
                room = o.str("cdmc").orEmpty(),
                teacher = o.str("xm").orEmpty(),
                campus = o.str("xqumc").ifNullOr { o.str("xqmc") }.orEmpty(),
            )
            val key = "${courseCode}|$classId"
            byClassKey.getOrPut(key) {
                Builder(
                    name = name,
                    courseCode = courseCode,
                    className = o.str("jxbmc").orEmpty(),
                    classId = classId,
                    credit = o.str("xf").orEmpty(),
                )
            }.sessions.add(session)
        }

        return byClassKey.values.map {
            ParsedCourse(it.name, it.courseCode, it.className, it.classId, it.credit, resolveConflicts(it.sessions.toList()))
        }
    }

    /**
     * 调课消解：同学期同课、同星期同节次、周次有交集 → 视为冲突，
     * 保留周次覆盖更长的一条（常规记录通常带原教师且周次更长）；平手保留先出现的。
     * 注意：仅在导入时执行，老版本导入的存量数据不回溯——需重新导入才生效。
     */
    private fun resolveConflicts(sessions: List<ParsedSession>): List<ParsedSession> {
        val result = mutableListOf<ParsedSession>()
        for (s in sessions) {
            val hit = result.indexOfFirst {
                it.weekday == s.weekday && it.startNode == s.startNode && it.endNode == s.endNode &&
                    (it.weeksMask and s.weeksMask) != 0
            }
            when {
                hit < 0 -> result.add(s)
                Integer.bitCount(s.weeksMask) > Integer.bitCount(result[hit].weeksMask) -> result[hit] = s
                // 已存的覆盖更长或相等：丢弃后到的调课记录
            }
        }
        return result
    }

    /** 解析节次："3-4" → (3,4)；单节 "3" → (3,3)；jc 文本回退如 "第3-4节"→(3,4)、"第10节"→(10,10) */
    private fun parseNodes(jcs: String?, jc: String?): Pair<Int, Int>? {
        val primary = extractNodes(jcs)
        if (primary != null) return primary
        return extractNodes(jc?.replace("第", "")?.replace("节", ""))
    }

    private fun extractNodes(text: String?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) return null
        val m = Regex("""(\d+)\s*[-–—]\s*(\d+)""").find(text)
        if (m != null) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            return if (a in 1..20 && b in 1..20 && a <= b) a to b else null
        }
        val n = Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: return null
        return if (n in 1..20) n to n else null
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString || it.content.isNotBlank() }
            ?.let { prim -> prim.content.takeIf { c -> c.isNotBlank() && c != "null" } }

    private inline fun <T> T.ifNullOr(fallback: () -> T): T = if (this == null) fallback() else this
}
