package com.heybox.checkin.data

/**
 * GitHub 下载线路：直连 + 几个前缀型加速镜像。
 *
 * 直连 GitHub 在国内时通时不通（拿到 404 / 连接被重置都见过），所以下载前先并发探一遍，
 * 用最先回数据的那条。镜像只负责搬运：装包时系统仍会校验签名，签名不一致的包装不上去。
 */
internal object UpdateSources {

    // 顺序无所谓，实际按探测延迟排；留几个是因为不同网络下总有几条是通的
    private val mirrors = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://gh.llkk.cc/",
        "https://hub.gitmirror.com/",
        "https://gh-proxy.net/",
    )

    /** 直连排第一，后面是镜像；非 GitHub 的地址（手动建的 release 之类）原样返回。 */
    fun candidates(apkUrl: String): List<String> {
        val trimmed = apkUrl.trim()
        if (!trimmed.startsWith("https://github.com/")) return listOf(trimmed)
        return (listOf(trimmed) + mirrors.map { it + trimmed }).distinct()
    }

    /** 给界面用的短名字，比如 `gh-proxy.com`。 */
    fun label(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: "直连"
}
