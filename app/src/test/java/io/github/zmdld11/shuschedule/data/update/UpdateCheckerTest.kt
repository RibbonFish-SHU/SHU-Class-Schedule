package io.github.zmdld11.shuschedule.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val releaseJson = """
    {
      "tag_name": "v0.3.5",
      "name": "v0.3.5",
      "body": "## v0.3.5\n- 手动调休\n- 小组件时间感知",
      "html_url": "https://github.com/zmdld11/SHU-Class-Schedule/releases/tag/v0.3.5",
      "assets": [
        {"name": "shu-schedule-v0.3.5.apk", "browser_download_url": "https://example.com/a.apk"},
        {"name": "checksums.txt", "browser_download_url": "https://example.com/b.txt"}
      ]
    }
    """.trimIndent()

    @Test
    fun parsesLatestRelease() {
        val info = UpdateChecker.parseLatest(releaseJson)!!
        assertEquals("v0.3.5", info.tagName)
        assertEquals("0.3.5", info.versionName)
        assertEquals("https://example.com/a.apk", info.apkUrl)
        assertTrue(info.body.contains("手动调休"))
        assertTrue(info.htmlUrl.contains("releases/tag/v0.3.5"))
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(UpdateChecker.parseLatest("not json"))
        assertNull(UpdateChecker.parseLatest("""{"assets":[]}""")) // 缺 tag_name
    }

    @Test
    fun versionComparison() {
        assertTrue(UpdateChecker.isNewer("0.3.4", "0.3.5"))
        assertTrue(UpdateChecker.isNewer("0.3.9", "0.3.10"))
        assertTrue(UpdateChecker.isNewer("0.3", "0.3.1"))
        assertFalse(UpdateChecker.isNewer("0.3.5", "0.3.5"))
        assertFalse(UpdateChecker.isNewer("0.4.0", "0.3.9"))
    }
}
