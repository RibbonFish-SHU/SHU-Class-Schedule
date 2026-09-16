package io.github.zmdld11.shuschedule.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub Releases 最新版信息解析与版本比较（纯函数，可单测） */
object UpdateChecker {

    data class ReleaseInfo(
        val tagName: String,     // "v0.3.5"
        val versionName: String, // "0.3.5"
        val name: String,        // release 标题
        val body: String,        // 更新说明（changelog 正文）
        val apkUrl: String,      // apk 资源直链
        val htmlUrl: String,     // release 页面
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatest(jsonText: String): ReleaseInfo? = runCatching {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
        val apkUrl = root["assets"]?.jsonArray
            ?.mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull to it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.firstOrNull { (_, name) -> name?.endsWith(".apk") == true }
            ?.first
            .orEmpty()
        ReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            name = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            body = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkUrl = apkUrl,
            htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.getOrNull()

    /** 点分版本号逐段数值比较：latest > current 才为真 */
    fun isNewer(current: String, latest: String): Boolean {
        val a = current.split('.').map { it.toIntOrNull() ?: 0 }
        val b = latest.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }
}
