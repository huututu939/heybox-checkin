package com.heybox.checkin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远端最新版本信息。
 *
 * [url] 是 Release 页面；[apkUrl]/[apkSize]/[shaUrl] 来自 Release 附件，
 * 有直链才能应用内更新；[notes] 是发版说明前几行，弹窗里给用户看一眼改了什么。
 */
data class UpdateInfo(
    val tag: String,
    val url: String,
    val apkUrl: String? = null,
    val apkSize: Long? = null,
    val shaUrl: String? = null,
    val notes: String? = null,
)

/**
 * 版本自检：每次打开 App 查一次，外加「关于 → 检查更新」手动查；不后台轮询。
 *
 * 存在的理由：官方设备指纹 SDK 或 APP 版本一变，本程序就会在登录时主动失败，
 * 用户需要知道「不是账号的问题，是包太旧了」以及去哪里拿新包。
 */
object UpdateCheck {

    /** Releases 最新页：检查失败（墙、限流）时也让用户能手动打开。 */
    const val RELEASES_URL = "https://github.com/huututu939/heybox-checkin/releases/latest"

    /**
     * 自动检查的最小间隔。
     *
     * GitHub 未认证接口按 IP 限流 60 次/小时，而首页每次冷启动都会查一次：
     * 反复开关 App 会把自己查进 403，之后再想查就得等限流窗口过去。
     * 12 小时对「一天内出新版」已经够快，手动检查不受这个限制。
     */
    const val AUTO_INTERVAL_MS = 12L * 3600 * 1000

    /** 上次没查成功时早一点再试（断网、被墙都是暂时的），但别每分钟都打。 */
    const val AUTO_RETRY_MS = 3600 * 1000L

    internal fun shouldAutoCheck(
        lastCheckedAt: Long,
        lastOk: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (lastCheckedAt <= 0L) return true
        // 时钟回拨 / 改过系统时间：别把用户永久卡在「不检查」
        if (lastCheckedAt > now) return true
        return now - lastCheckedAt >= if (lastOk) AUTO_INTERVAL_MS else AUTO_RETRY_MS
    }

    private const val API =
        "https://api.github.com/repos/huututu939/heybox-checkin/releases/latest"

    suspend fun latest(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching { parse(read(API)) }
    }

    private fun read(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "heybox-checkin")
        }
        try {
            val code = conn.responseCode
            require(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(body: String): UpdateInfo {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        require(tag.isNotEmpty()) { "响应里没有 tag_name" }
        val url = json.optString("html_url").trim().ifEmpty { RELEASES_URL }
        val assets = json.optJSONArray("assets")
            ?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } }
            .orEmpty()
        fun assetUrl(predicate: (String) -> Boolean): String? = assets
            .firstOrNull { predicate(it.optString("name")) }
            ?.optString("browser_download_url")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val apk = assets.firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
        return UpdateInfo(
            tag = tag,
            url = url,
            apkUrl = apk?.optString("browser_download_url")?.trim()?.takeIf { it.isNotEmpty() },
            apkSize = apk?.optLong("size")?.takeIf { it > 0L },
            shaUrl = assetUrl { it.endsWith(".apk.sha256", ignoreCase = true) },
            notes = json.optString("body").let(::summarizeNotes),
        )
    }

    /** [latest] 是否比 [current] 新。两边的 `v` 前缀、缺省段、`-beta` 后缀都能处理。 */
    internal fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    internal fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /**
     * 发版说明只留前几行正文：弹窗里放不下整篇，标题符号和空行都去掉。
     */
    internal fun summarizeNotes(body: String, maxChars: Int = 160): String? {
        // 只要第一段正文：标题（## 盒签 vX）和「新增 / 安装」这些小标题对用户没信息量，
        // 弹窗里塞五行反而没人看
        val paragraphs = body.replace("\r\n", "\n")
            .split(Regex("\n\\s*\n"))
            .map { paragraph ->
                val header = paragraph.trimStart().startsWith("#")
                val text = paragraph.lineSequence()
                    .map { line ->
                        line.trim().trimStart('#').trim()
                            .removePrefix("- ").removePrefix("* ").trim()
                    }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .replace("**", "")
                    .replace("`", "")
                header to text
            }
            .filter { (header, text) -> !header && text.isNotBlank() }
            .map { it.second }

        val first = paragraphs.firstOrNull() ?: return null
        return first.take(maxChars).trim().ifEmpty { null }
    }

    private fun segments(v: String): List<Int> =
        v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /**
     * 登录失败文案里出现这些词，基本就是包太旧，而不是网络或验证码的问题。
     * 要求同时带「失败」，否则「正在获取设备令牌…」这类进度文案也会被误判。
     */
    internal fun looksOutdated(message: String): Boolean =
        message.contains("失败") &&
            listOf("设备令牌", "版本", "更新程序", "签名").any { message.contains(it) }
}
