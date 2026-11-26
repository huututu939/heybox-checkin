package com.heybox.checkin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用内更新：把 Release 里那个 app-release.apk 下到缓存目录，再交给系统安装器。
 *
 * 这里不做任何校验之外的自作聪明：包仍然由系统安装器校验签名，
 * 所以装上去的必须和当前应用同签名（本项目历次 Release 都是同一把 key）。
 */
object ApkInstaller {

    private const val MIME = "application/vnd.android.package-archive"

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
     * 下载到 `cacheDir/updates/`。[onProgress] 传 null 表示拿不到总长度，只能显示不确定进度。
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "heybox-update.apk")
        target.delete()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "heybox-checkin")
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
            // GitHub 偶尔会拿 200 回一页 HTML（限流/风控），这种交给安装器只会报解析失败
            require(target.looksLikeApk()) { "下载到的文件不是 APK" }
            onProgress(1f)
            target
        } finally {
            conn.disconnect()
        }
    }

    private fun File.looksLikeApk(): Boolean = inputStream().use { input ->
        val head = ByteArray(2)
        input.read(head) == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
