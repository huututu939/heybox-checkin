package com.heybox.checkin.data

import android.app.AlarmManager
import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.heybox.checkin.work.Watchdog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 自检里的一条：说清「查的是什么、过没过、没过该怎么办」。 */
data class CheckItem(
    val label: String,
    val ok: Boolean,
    val detail: String,
)

/**
 * 把「某天没跑」这类问题拆成可自查的几项：登录、通知、省电、精确闹钟、定时、看门狗、
 * 设备令牌、接口连通。全部只读本地状态 + 一次匿名 GET，不发送任何账号信息。
 */
suspend fun runSelfCheck(context: Context): List<CheckItem> = withContext(Dispatchers.IO) {
    val store = SessionStore(context)
    val config = store.load()
    buildList {
        add(
            CheckItem(
                label = "登录状态",
                ok = config != null && !store.sessionExpired(),
                detail = when {
                    config == null -> "没有可用凭据，需要登录"
                    store.sessionExpired() -> "登录已过期，点通知或首页横幅重新登录"
                    else -> "正常"
                },
            ),
        )
        add(
            CheckItem(
                label = "通知权限",
                ok = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                detail = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    "已允许"
                } else {
                    "已关闭，签到结果不会提醒"
                },
            ),
        )
        add(
            CheckItem(
                label = "忽略电池优化",
                ok = batteryOk(context),
                detail = if (batteryOk(context)) "已忽略" else "未忽略，夜间容易被系统清理",
            ),
        )
        add(
            CheckItem(
                label = "精确闹钟",
                ok = exactAlarmOk(context),
                detail = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "系统版本无需该权限"
                    exactAlarmOk(context) -> "已允许"
                    else -> "未允许，触发时间可能推迟"
                },
            ),
        )
        val scheduled = store.scheduledEnabled()
        val next = store.nextTrigger()
        add(
            CheckItem(
                label = "自动签到",
                ok = scheduled && next > System.currentTimeMillis(),
                detail = when {
                    !scheduled -> "已关闭"
                    next <= 0L -> "已开启，但系统没有排上闹钟"
                    else -> "已排定下一次"
                },
            ),
        )
        add(
            CheckItem(
                label = "值守看门狗",
                ok = watchdogScheduled(context),
                detail = if (watchdogScheduled(context)) "已注册" else "未注册，闹钟被清后没人补",
            ),
        )
        add(
            CheckItem(
                label = "设备令牌",
                ok = config?.session?.tokenId.isNullOrBlank().not(),
                detail = if (config?.session?.tokenId.isNullOrBlank() == false) {
                    "已生成"
                } else {
                    "缺少设备令牌，重新登录可修复"
                },
            ),
        )
        val api = apiReachable()
        add(CheckItem(label = "接口连通", ok = api.first, detail = api.second))
    }
}

private fun batteryOk(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

private fun exactAlarmOk(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return manager.canScheduleExactAlarms()
}

private fun watchdogScheduled(context: Context): Boolean {
    val jobs = (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)
        ?.allPendingJobs
        .orEmpty()
    return jobs.any { it.id == Watchdog.JOB_ID }
}

/** 只求「能不能连上」，不读内容；4xx 也算通，5xx 与异常算不通。 */
private fun apiReachable(): Pair<Boolean, String> = runCatching {
    val connection = (URL("https://api.xiaoheihe.cn/").openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        requestMethod = "GET"
        instanceFollowRedirects = false
    }
    try {
        val code = connection.responseCode
        Pair(code in 200..499, "HTTP $code")
    } finally {
        connection.disconnect()
    }
}.getOrElse {
    Pair(false, "连接失败：${it.javaClass.simpleName}")
}

/** 复制到剪贴板时的文本。 */
fun selfCheckText(items: List<CheckItem>): String = buildString {
    appendLine("盒签自检")
    items.forEach { item ->
        appendLine("${if (item.ok) "✓" else "✕"} ${item.label}：${item.detail}")
    }
}
