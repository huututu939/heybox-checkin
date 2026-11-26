package com.heybox.checkin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 更新日志里的一条改动：[section] 是「新增 / 优化 / 修复 / 安全」，[text] 是条目正文。 */
data class NoteItem(val section: String, val text: String)

/**
 * 远端最新版本信息。
 *
 * [url] 是 Release 页面；[apkUrl]/[apkSize]/[shaUrl] 来自 Release 附件，
 * 有直链才能应用内更新；[changes] 是按分节拆好的改动条目，弹窗按条展示；
 * [notes] 是解析失败时的兜底纯文本。
 */
data class UpdateInfo(
    val tag: String,
    val url: String,
    val apkUrl: String? = null,
    val apkSize: Long? = null,
    val shaUrl: String? = null,
    val notes: String? = null,
    val changes: List<NoteItem> = emptyList(),
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
        val body = json.optString("body")
        return UpdateInfo(
            tag = tag,
            url = url,
            apkUrl = apk?.optString("browser_download_url")?.trim()?.takeIf { it.isNotEmpty() },
            apkSize = apk?.optLong("size")?.takeIf { it > 0L },
            shaUrl = assetUrl { it.endsWith(".apk.sha256", ignoreCase = true) },
            notes = body.let(::summarizeNotes),
            changes = parseChanges(body),
        )
    }

    /** 只认用户关心的分节；「工程」「安装」「校验」这些不进弹窗。 */
    private val SECTIONS = setOf("新增", "优化", "修复", "安全")

    /**
     * 把 Release 正文拆成「分节 + 条目」。
     *
     * 更新日志固定按 `### 新增 / 优化 / 修复` 分条写，弹窗据此逐条显示，
     * 用户不用在弹窗里读一整段。正文没有分节（老版本日志）时返回空，
     * 由 [summarizeNotes] 兜底。
     */
    internal fun parseChanges(body: String, limit: Int = Int.MAX_VALUE): List<NoteItem> {
        val items = ArrayList<NoteItem>()
        var section: String? = null
        for (raw in body.replace("\r\n", "\n").lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#")) {
                section = line.trimStart('#').trim().takeIf { it in SECTIONS }
                continue
            }
            val current = section ?: continue
            if (!line.startsWith("- ") && !line.startsWith("* ")) continue
            val text = line.removePrefix("- ").removePrefix("* ")
                .replace("**", "")
                .replace("`", "")
                .trim()
            if (text.isEmpty()) continue
            items += NoteItem(current, text)
            if (items.size >= limit) break
        }
        return items
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
     * 发版说明取第一段正文的第一条，给弹窗当「这版有什么」的一行预览。
     *
     * 更新日志按 `新增 / 优化 / 修复` 分条写，一条一行，所以取第一条就是最想让人看到的那条；
     * 把整段拼起来会在弹窗里堆成一面墙。完整清单去 Release 页面看。
     */
    internal fun summarizeNotes(body: String, maxChars: Int = 160): String? {
        val item = body.replace("\r\n", "\n")
            .split(Regex("\n\\s*\n"))
            // 标题行（## 盒签 vX、### 新增）对用户没有信息量
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .flatMap { paragraph -> paragraph.lineSequence() }
            .map { line ->
                line.trim().trimStart('#').trim()
                    .removePrefix("- ").removePrefix("* ").trim()
            }
            .firstOrNull { it.isNotEmpty() }
            ?.replace("**", "")
            ?.replace("`", "")
            ?: return null

        val cut = item.take(maxChars).trim()
        if (cut.isEmpty()) return null
        // 截断时补省略号，否则弹窗里像是排版坏了
        return if (item.length > maxChars) "$cut…" else cut
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
