package io.github.zmdld11.shuschedule.data.jwxk

import io.github.zmdld11.shuschedule.data.db.TermType
import io.github.zmdld11.shuschedule.data.parser.SemesterCodes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 教务下拉框里的一个选项：value 是请求用的真实编码 */
data class SemesterOption(
    val value: Int,
    val label: String,
    val selected: Boolean,
) {
    val display: String get() = label.ifBlank { value.toString() }
}

data class SemesterChoices(
    val years: List<SemesterOption>,
    val terms: List<SemesterOption>,
)

/**
 * 解析采集脚本回传的学年/学期选项 JSON：
 * `{ok: true, years: [{value, label, selected}...], terms: [...]}`。
 * 丢弃空 value（占位项）；任一列表为空视为解析失败返回 null。
 */
object SemesterOptionsParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): SemesterChoices? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["ok"]?.jsonPrimitive?.content != "true") return@runCatching null
        val years = options(root, "years", 1900..2100)
        val terms = options(root, "terms", 1..999)
        if (years.isEmpty() || terms.isEmpty()) null else SemesterChoices(years, terms)
    }.getOrNull()

    /** 选项 value 的编码含义随部署而变，TermType 优先看教务自己的季节用词 */
    fun termTypeOf(option: SemesterOption): TermType? =
        SemesterCodes.termTypeOf(option.label, option.value)

    private fun options(
        root: kotlinx.serialization.json.JsonObject,
        key: String,
        valueRange: IntRange,
    ): List<SemesterOption> {
        val list = root[key] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return list.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val value = (o["value"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.toIntOrNull()
                ?.takeIf { it in valueRange } ?: return@mapNotNull null
            SemesterOption(
                value = value,
                label = (o["label"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim().orEmpty(),
                selected = o["selected"]?.jsonPrimitive?.content == "true",
            )
        }
    }
}
