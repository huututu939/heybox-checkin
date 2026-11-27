package com.heybox.checkin.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.historyCsv
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把历史写成 cacheDir/exports 下的 CSV，再走系统分享。
 *
 * 用分享而不是直接写公共目录：免存储权限，也能发到微信/邮件/网盘，用户自己决定去哪。
 */
internal fun shareHistoryCsv(context: Context, history: List<RunRecord>) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val name = "盒签记录-${SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())}.csv"
    val file = File(dir, name)
    file.writeText(historyCsv(history), Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    // 部分精简 ROM / 模拟器没有注册 text/csv，退回 text/plain 至少还能发到短信、邮件或网盘
    val type = if (context.hasSendTarget("text/csv")) "text/csv" else "text/plain"
    val intent = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "盒签签到记录")
        // 分享面板跑在另一个进程里，只有 setClipData 才能把 URI 读权限带过去；
        // 只加 FLAG_GRANT_READ_URI_PERMISSION 时 chooser 会报 Permission Denial。
        clipData = ClipData.newRawUri("盒签签到记录", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "导出签到记录").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

private fun Context.hasSendTarget(type: String): Boolean =
    packageManager.queryIntentActivities(
        Intent(Intent.ACTION_SEND).setType(type),
        0,
    ).isNotEmpty()
