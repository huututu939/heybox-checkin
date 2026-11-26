package com.heybox.checkin

import com.heybox.checkin.data.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `patch 升级算新版本`() {
        assertTrue(UpdateCheck.isNewer("v1.4.2", "v1.4.1"))
    }

    @Test
    fun `v 前缀不影响比较`() {
        assertFalse(UpdateCheck.isNewer("1.4.1", "v1.4.1"))
        assertFalse(UpdateCheck.isNewer("v1.4.1", "1.4.1"))
    }

    @Test
    fun `段位按数字比较而不是字符串`() {
        assertTrue(UpdateCheck.isNewer("v1.10.0", "v1.9.9"))
        assertFalse(UpdateCheck.isNewer("v1.9.9", "v1.10.0"))
    }

    @Test
    fun `缺省段按 0 处理`() {
        assertFalse(UpdateCheck.isNewer("v1.4", "1.4.0"))
        assertTrue(UpdateCheck.isNewer("v1.5", "1.4.9"))
    }

    @Test
    fun `更旧的版本不算新`() {
        assertFalse(UpdateCheck.isNewer("v1.3.9", "v1.4.1"))
    }

    @Test
    fun `预发布后缀不当作新版本`() {
        assertFalse(UpdateCheck.isNewer("v1.4.1-beta", "v1.4.1"))
        assertTrue(UpdateCheck.isNewer("v1.4.2-beta", "v1.4.1"))
    }

    @Test
    fun `解析 GitHub release 响应`() {
        val info = UpdateCheck.parse(
            """{"tag_name":"v1.5.0","html_url":"https://github.com/huututu939/heybox-checkin/releases/tag/v1.5.0"}"""
        )
        assertEquals("v1.5.0", info.tag)
        assertTrue(info.url.endsWith("/v1.5.0"))
    }

    @Test
    fun `从 assets 里挑出 apk 直链`() {
        val info = UpdateCheck.parse(
            """{"tag_name":"v1.5.1","html_url":"https://github.com/huututu939/heybox-checkin/releases/tag/v1.5.1","assets":[{"name":"app-release.apk.sha256","browser_download_url":"https://example.com/a.sha256"},{"name":"app-release.apk","browser_download_url":"https://example.com/a.apk"}]}"""
        )
        assertEquals("https://example.com/a.apk", info.apkUrl)
    }

    @Test
    fun `解析附件大小和 sha256 直链`() {
        val info = UpdateCheck.parse(
            """{"tag_name":"v1.5.2","assets":[{"name":"app-release.apk","size":8788078,"browser_download_url":"https://example.com/a.apk"},{"name":"app-release.apk.sha256","size":82,"browser_download_url":"https://example.com/a.sha256"}]}"""
        )
        assertEquals(8788078L, info.apkSize)
        assertEquals("https://example.com/a.sha256", info.shaUrl)
    }

    @Test
    fun `发版说明只留前几行正文`() {
        val notes = UpdateCheck.summarizeNotes(
            """## 盒签 v1.5.2

### 新增

- **打开 App 自动查一次**：有新版就弹窗
- 应用内更新

### 安装

1. 下载 app-release.apk"""
        )
        assertTrue(notes!!.startsWith("打开 App 自动查一次"))
        assertFalse(notes.contains("##"))
        assertFalse(notes.contains("**"))
        assertFalse(notes.contains("盒签 v1.5.2"))
    }

    @Test
    fun `没有 apk 附件时直链为空`() {
        val info = UpdateCheck.parse(
            """{"tag_name":"v1.5.1","assets":[{"name":"notes.txt","browser_download_url":"https://example.com/n.txt"}]}"""
        )
        assertEquals(null, info.apkUrl)
    }

    @Test
    fun `缺少 html_url 时回落到 releases 页`() {
        val info = UpdateCheck.parse("""{"tag_name":"v1.5.0"}""")
        assertEquals(UpdateCheck.RELEASES_URL, info.url)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `缺少 tag_name 直接失败`() {
        UpdateCheck.parse("""{"html_url":"https://example.com"}""")
    }

    @Test
    fun `设备令牌失败文案会提示去下载页`() {
        val message = "发送失败：官网设备令牌组件已更新（SHA-256 不匹配），需要更新程序后再试"
        assertTrue(UpdateCheck.looksOutdated(message))
    }

    @Test
    fun `普通进度文案不触发提示`() {
        assertFalse(UpdateCheck.looksOutdated("正在获取设备令牌并请求短信…"))
        assertFalse(UpdateCheck.looksOutdated("发送失败：HTTP 500"))
        assertFalse(UpdateCheck.looksOutdated(""))
    }

    private val now = 1_800_000_000_000L

    @Test
    fun `从没查过就查一次`() {
        assertTrue(UpdateCheck.shouldAutoCheck(0L, now = now))
    }

    @Test
    fun `十二小时内不重复查`() {
        assertFalse(UpdateCheck.shouldAutoCheck(now - 60_000L, now = now))
        assertFalse(UpdateCheck.shouldAutoCheck(now - 11 * 3600_000L, now = now))
    }

    @Test
    fun `超过十二小时再查`() {
        assertTrue(UpdateCheck.shouldAutoCheck(now - 13 * 3600_000L, now = now))
    }

    @Test
    fun `上次失败只等一小时`() {
        assertFalse(UpdateCheck.shouldAutoCheck(now - 10 * 60_000L, lastOk = false, now = now))
        assertTrue(UpdateCheck.shouldAutoCheck(now - 2 * 3600_000L, lastOk = false, now = now))
    }

    @Test
    fun `系统时间被改到过去也不会永久不查`() {
        assertTrue(UpdateCheck.shouldAutoCheck(now + 3600_000L, now = now))
    }
}
