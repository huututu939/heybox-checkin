package com.heybox.checkin.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 应用内更新：把 Release 里那个 app-release.apk 下到缓存目录，再交给系统安装器。
 *
 * 下载走 [UpdateSources] 探出来的最快线路；装之前会比对签名，
 * 镜像被劫持、换了包的会被直接拦下（系统安装器本身也会再校验一次）。
 */
object ApkInstaller {

    private const val MIME = "application/vnd.android.package-archive"
    private const val UA = "heybox-checkin"

    /** 8.0 起「安装未知应用」是逐应用开关，没开的话连安装器都调不起来。 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * 先并发探一遍所有线路，再按首字节延迟从快到慢依次试，哪条能下完就用哪条。
     * [onSource] 回报当前地址（界面显示成线路名），[onProgress] 传 null 表示总长度未知。
     */
    suspend fun download(
        context: Context,
        apkUrl: String,
        shaUrl: String? = null,
        onSource: (String) -> Unit,
        onProgress: (Float?) -> Unit,
    ): File {
        val extra = UpdateSources.remoteMirrors()
        val ranked = rank(UpdateSources.candidates(apkUrl, extra))
        // 校验和先取：拿不到就不做这一道，签名校验仍然在
        val expected = shaUrl?.let { url ->
            runCatching { withContext(Dispatchers.IO) { fetchSha(url, extra) } }.getOrNull()
        }
        var lastError: Throwable? = null
        for (url in ranked) {
            onSource(url)
            onProgress(0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = fetch(context, url, onProgress)
                    if (expected != null) require(file.sha256() == expected) { "校验和不一致" }
                    file
                }
            }
            result.getOrNull()?.let { return it }
            lastError = result.exceptionOrNull() ?: lastError
        }
        throw lastError ?: IllegalStateException("没有可用的下载线路")
    }

    /** Release 里那个 `.sha256` 小文件，走同样的线路，取第一条能拿到的。 */
    private suspend fun fetchSha(shaUrl: String, extra: List<String>): String? {
        for (url in rank(UpdateSources.candidates(shaUrl, extra))) {
            val text = runCatching { readText(url) }.getOrNull() ?: continue
            val token = text.trim().substringBefore(' ').lowercase()
            if (token.length == 64 && token.all { it in "0123456789abcdef" }) return token
        }
        return null
    }

    private fun readText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 6_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
        return try {
            require(conn.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun rank(candidates: List<String>): List<String> = coroutineScope {
        // 探测最多等 8 秒：某条线路的 DNS 卡住时不能把整次更新一起拖死
        val probed = withTimeoutOrNull(8_000) {
            candidates
                .map { url -> async(Dispatchers.IO) { url to probe(url) } }
                .awaitAll()
        }.orEmpty()
        // 能连通的按首字节时间排；没探完或失败的保持原顺序兜底
        val ordered = probed.filter { it.second != null }.sortedBy { it.second }.map { it.first }
        ordered + candidates.filterNot { it in ordered }
    }

    /** 用一个字节的 Range 请求量首字节延迟；非 200/206 视为不可用。 */
    private fun probe(url: String): Long? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=0-0")
                setRequestProperty("User-Agent", UA)
            }
            val start = SystemClock.elapsedRealtime()
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) return null
            conn.inputStream.use { it.read() }
            SystemClock.elapsedRealtime() - start
        } catch (e: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun fetch(
        context: Context,
        url: String,
        onProgress: (Float?) -> Unit,
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "heybox-update.apk")
        target.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            require(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(if (total > 0L) read.toFloat() / total else null)
                    }
                }
            }
            require(read > 0L) { "下载内容为空" }
            // 镜像偶尔会拿 200 回一页 HTML（限流/风控），这种交给安装器只会报解析失败
            require(target.looksLikeApk()) { "下载到的文件不是 APK" }
            onProgress(1f)
            return target
        } finally {
            conn.disconnect()
        }
    }

    private fun File.looksLikeApk(): Boolean = inputStream().use { input ->
        val head = ByteArray(2)
        input.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    }

    fun install(context: Context, file: File) {
        require(signedLikeUs(context, file)) { "下载到的包签名与本机不一致，已取消安装" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 包必须和当前应用同签名，否则系统安装器也会拒，先在这里给出人话错误。 */
    private fun signedLikeUs(context: Context, file: File): Boolean {
        val pm = context.packageManager
        val mine = runCatching {
            pm.getPackageInfo(context.packageName, signerFlags())
        }.getOrNull()?.let(::signerDigest) ?: return false
        val theirs = runCatching {
            pm.getPackageArchiveInfo(file.absolutePath, signerFlags())
        }.getOrNull()?.let(::signerDigest) ?: return false
        return mine.contentEquals(theirs)
    }

    @Suppress("DEPRECATION")
    private fun signerFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signerDigest(info: PackageInfo): ByteArray? {
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
    }
}
