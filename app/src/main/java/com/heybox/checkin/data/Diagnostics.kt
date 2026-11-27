package com.heybox.checkin.data

import android.content.Context
import android.os.Build
import com.heybox.checkin.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断信息：把「版本 / 机型 / 权限 / 调度 / 上次结果 / 最近崩溃」拼成一段可复制的文本。
 * 只放排查必需的环境与状态，不带 heybox_id、pkey、token 等凭据。
 */
object Diagnostics {

    private const val CRASH_FILE = "last_crash.txt"

    fun report(context: Context): String {
        val store = SessionStore(context)
        val config = store.load()
        val sb = StringBuilder()
        sb.appendLine("盒签 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("包名: ${context.packageName}")
        sb.appendLine("系统: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("登录态: " + when {
            config == null -> "未登录"
            store.sessionExpired() -> "已过期，待重新登录"
            else -> "已登录"
        })
        sb.appendLine("定时: " + if (store.scheduledEnabled()) {
            val next = store.nextTrigger()
            if (next > 0) "开启，下次 ${time(next)}" else "开启，下次时间未排定"
        } else "关闭")
        sb.appendLine(
            "通知权限: " +
                (PermissionState.notificationBlockedReason(context)?.let { "不可用（$it）" } ?: "已允许"),
        )
        sb.appendLine(
            "精确闹钟: " + when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "无需（API < 31）"
                PermissionState.exactAlarmAllowed(context) -> "已授权"
                else -> "未授权"
            },
        )
        sb.appendLine("电池优化: " + if (PermissionState.batteryAllowed(context)) "已忽略限制" else "未忽略")
        sb.appendLine("今日已执行: ${store.ranToday()}")
        sb.appendLine("上次结果: ${store.lastResult()?.take(300) ?: "无"}")
        val crash = readCrash(context)
        sb.appendLine("最近崩溃: " + (crash?.take(600)?.replace("\n", " | ") ?: "无"))
        return sb.toString().trim()
    }

    fun readCrash(context: Context): String? = runCatching {
        val f = File(context.filesDir, CRASH_FILE)
        if (f.exists()) f.readText() else null
    }.getOrNull()

    /** 全局异常兜底：把崩溃写进 filesDir，再交给系统默认处理器，不吞异常。 */
    fun installCrashHandler(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("时间: ${time(System.currentTimeMillis())}")
                    appendLine("线程: ${thread.name}")
                    appendLine(error.stackTraceToString())
                }
                File(app.filesDir, CRASH_FILE).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun time(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
}
