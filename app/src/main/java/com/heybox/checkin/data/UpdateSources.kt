package com.heybox.checkin.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub 下载线路：直连 + 几个前缀型加速镜像。
 *
 * 直连 GitHub 在国内时通时不通（拿到 404 / 连接被重置都见过），所以下载前先并发探一遍，
 * 用最先回数据的那条。镜像只负责搬运：装包时系统仍会校验签名，签名不一致的包装不上去。
 */
internal object UpdateSources {

    /**
     * 镜像列表可以远程更新：内置的会过期，这里从 jsDelivr（国内一般能通）拉一份。
     * 拉不到就只用内置的；远程只做追加，写坏了也不会把内置线路挡掉。
     */
    private const val REMOTE_LIST =
        "https://cdn.jsdelivr.net/gh/huututu939/heybox-checkin@main/docs/mirrors.json"

    @Volatile
    private var remote: List<String>? = null

    // 顺序无所谓，实际按探测延迟排；留几个是因为不同网络下总有几条是通的
    private val builtIn = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://gh.llkk.cc/",
        "https://hub.gitmirror.com/",
        "https://gh-proxy.net/",
    )

    /** 直连排第一，后面是镜像；非 GitHub 的地址（手动建的 release 之类）原样返回。 */
    fun candidates(apkUrl: String, extra: List<String> = emptyList()): List<String> {
        val trimmed = apkUrl.trim()
        if (!trimmed.startsWith("https://github.com/")) return listOf(trimmed)
        return (listOf(trimmed) + (extra + builtIn).map { it + trimmed }).distinct()
    }

    /** 拉一次远程镜像列表，进程内缓存；失败就静默回落。 */
    suspend fun remoteMirrors(): List<String> {
        remote?.let { return it }
        val loaded = runCatching { withContext(Dispatchers.IO) { fetchRemote() } }.getOrDefault(emptyList())
        remote = loaded
        return loaded
    }

    private fun fetchRemote(): List<String> {
        val conn = (URL(REMOTE_LIST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "heybox-checkin")
        }
        return try {
            require(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            org.json.JSONArray(text).let { array ->
                (0 until array.length())
                    .map { array.optString(it).trim() }
                    .filter { it.startsWith("https://") && it.endsWith("/") }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 给界面用的短名字，比如 `gh-proxy.com`。 */
    fun label(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: "直连"
}
