package com.heybox.checkin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远端最新版本信息。[url] 是 Release 页面，[apkUrl] 是 APK 直链（有的话就能应用内更新）。
 */
data class UpdateInfo(val tag: String, val url: String, val apkUrl: String? = null)

/**
 * 版本自检：每次打开 App 查一次，外加「关于 → 检查更新」手动查；不后台轮询。
 *
 * 存在的理由：官方设备指纹 SDK 或 APP 版本一变，本程序就会在登录时主动失败，
 * 用户需要知道「不是账号的问题，是包太旧了」以及去哪里拿新包。
 */
object UpdateCheck {

    /** Releases 最新页：检查失败（墙、限流）时也让用户能手动打开。 */
    const val RELEASES_URL = "https://github.com/huututu939/heybox-checkin/releases/latest"

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
        val apkUrl = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
        return UpdateInfo(tag, url, apkUrl)
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
