package io.github.zmdld11.shuschedule.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** 拉取 GitHub Releases 最新版（失败静默返回 null） */
@Singleton
class UpdateCheckClient @Inject constructor() {

    suspend fun fetchLatest(): UpdateChecker.ReleaseInfo? = withContext(Dispatchers.IO) {
        val text = runCatching {
            val conn = URL("https://api.github.com/repos/zmdld11/SHU-Class-Schedule/releases/latest")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@runCatching null
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext null
        UpdateChecker.parseLatest(text)
    }
}
