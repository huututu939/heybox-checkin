package com.heybox.checkin.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 用 WebView 跑官方设备指纹 SDK，拿 x_xhh_tokenid（"B" + 88 位）。
 * 这是整套方案唯一无法纯算法复现的一步。
 *
 * 排查清单：
 * 1. WebView 必须主线程创建，baseUrl 必须是 https://www.xiaoheihe.cn/
 * 2. 先注入 _smConf 再执行 SDK
 * 3. 需要能访问 fp-it.portal101.cn
 * 4. 拿不到多半是 WebView 指纹采集失败（硬件加速、老版本 WebView）
 */
class DeviceTokenProvider(private val context: Context) {

    private val sdkUrl = "https://static.portal101.cn/dist/web/v3.0.0/fp.min.js"
    private val tokenPattern = Regex("^B[A-Za-z0-9+/=_-]{88}$")

    private val smConf = """
        {"organization":"0yD85BjYvGFAvHaSQ1mc","appId":"heybox_website",
         "publicKey":"MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCXj9exmI4nQjmT52iwr+yf7hAQ06bfSZHTAHUfRBYiagCf/whhd8es0R79wBigpiHLd28TKA8b8mGR8OiiI1hV+qfynCWihvp3mdj8MiiH6SU3lhro2hkfYzImZB0RmWr2zE4Xt1+A6Oyp6bf+W7JSxYUXHw3nNv7Td4jw4jEFKQIDAQAB",
         "staticHost":"static.portal101.cn","protocol":"https","noNeedRetry":true}
    """.trimIndent()

    private val bootstrapHtml = """
        <!doctype html><html><head><meta charset="utf-8"></head><body>
        <script>window._smConf = __SM_CONF__; window._smReadyFuncs = []; window.SMSdk = {};</script>
        </body></html>
    """.trimIndent()

    suspend fun fetch(timeoutMs: Long = 25_000): String {
        val source = withContext(Dispatchers.IO) { downloadSdk() }
        return withContext(Dispatchers.Main) { runWebView(source, timeoutMs) }
    }

    private fun downloadSdk(): String {
        val conn = URL(sdkUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", CHROME_UA)
        return try {
            val code = conn.responseCode
            require(code == 200) { "设备令牌组件下载失败：HTTP $code" }
            val bytes = conn.inputStream.use { it.readBytes() }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            // 与上游同一份校验值：SDK 被替换或升级时宁可失败，也不执行来历不明的脚本。
            require(digest == SDK_SHA256) { "官网设备令牌组件已更新（SHA-256 不匹配），需要更新程序后再试" }
            bytes.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun runWebView(source: String, timeoutMs: Long): String =
        suspendCancellableCoroutine { cont ->
            val webView = WebView(context)
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            var poll: Runnable? = null

            fun cleanup() {
                poll?.let { handler.removeCallbacks(it) }
                webView.stopLoading()
                webView.destroy()
            }

            fun succeed(token: String) {
                if (finished) return
                finished = true
                cleanup()
                cont.resume(token)
            }

            fun fail(message: String) {
                if (finished) return
                finished = true
                cleanup()
                cont.resumeWithException(IllegalStateException(message))
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = CHROME_UA
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(source, null)

                    var attempts = 0
                    val maxAttempts = (timeoutMs / 100).toInt()
                    poll = object : Runnable {
                        override fun run() {
                            if (finished) return
                            view.evaluateJavascript(POLL_JS) { raw ->
                                if (finished) return@evaluateJavascript
                                val token = unquote(raw).trim()
                                if (tokenPattern.matches(token)) succeed(token)
                            }
                            attempts++
                            if (attempts >= maxAttempts) {
                                fail("等待设备令牌超时（SDK 未返回 B 开头 88 位令牌）")
                            } else {
                                handler.postDelayed(this, 100)
                            }
                        }
                    }
                    handler.post(poll!!)
                }
            }

            cont.invokeOnCancellation { fail("已取消") }

            webView.loadDataWithBaseURL(
                "https://www.xiaoheihe.cn/",
                bootstrapHtml.replace("__SM_CONF__", smConf),
                "text/html",
                "utf-8",
                null,
            )
        }

    private fun unquote(raw: String?): String {
        val value = raw ?: return ""
        if (value == "null" || value.isEmpty()) return ""
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1)
        }
        return value
    }

    companion object {
        private const val SDK_SHA256 = "654b94891624c759ba1b887b39261d8e894144da5f56a6dc42b5b6e0d66562f9"

        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.192 Mobile Safari/537.36"

        private const val POLL_JS =
            "(function(){try{if(window.SMSdk&&window.SMSdk.getDeviceId){var t=window.SMSdk.getDeviceId();return t?String(t):''}return ''}catch(e){return ''}})()"
    }
}
