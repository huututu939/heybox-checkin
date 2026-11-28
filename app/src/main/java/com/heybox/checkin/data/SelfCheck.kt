package com.heybox.checkin.data

import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
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
    /** ROM 的自启动管理这类探测不到的项：不参与 ✓/✕，只给行动建议。 */
    val verifiable: Boolean = true,
)

/**
 * 把「某天没跑」这类问题拆成可自查的几项：登录、通知、省电、精确闹钟、定时、看门狗、
 * 设备令牌、接口连通。权限判定统一走 [PermissionState]，全部只读本地状态 + 一次匿名 GET。
 */
suspend fun runSelfCheck(context: Context): List<CheckItem> = withContext(Dispatchers.IO) {
    val store = SessionStore(context)
    val config = store.load()
    // 每个谓词只算一次，ok 与 detail 用同一个结果，避免两次探测结果不一致
    val notifyBlocked = PermissionState.notificationBlockedReason(context)
    val battery = PermissionState.batteryAllowed(context)
    val exact = PermissionState.exactAlarmAllowed(context)
    val scheduled = store.scheduledEnabled()
    val next = store.nextTrigger()
    val watchdog = watchdogScheduled(context)
    val token = !config?.session?.tokenId.isNullOrBlank()

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
                ok = notifyBlocked == null,
                detail = notifyBlocked ?: "已允许",
            ),
        )
        add(
            CheckItem(
                label = "忽略电池优化",
                ok = battery,
                detail = if (battery) "已忽略" else "未忽略，夜间容易被系统清理",
            ),
        )
        add(
            CheckItem(
                label = "精确闹钟",
                ok = exact,
                detail = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "系统版本无需该权限"
                    exact -> "已允许"
                    else -> "未允许，触发时间可能推迟"
                },
            ),
        )
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
                ok = watchdog,
                detail = if (watchdog) "已注册" else "未注册，闹钟被清后没人补",
            ),
        )
        add(
            CheckItem(
                label = "设备令牌",
                ok = token,
                detail = if (token) "已生成" else "缺少设备令牌，重新登录可修复",
            ),
        )
        val api = apiReachable()
        add(CheckItem(label = "接口连通", ok = api.first, detail = api.second))
        add(
            CheckItem(
                label = "自启动 / 后台清理",
                ok = true,
                verifiable = false,
                detail = "App 探测不到 ROM 的管理策略：系统启动管理里把盒签设为手动管理，允许自启动与后台活动；最近任务里锁定卡片",
            ),
        )
    }
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
        val mark = when {
            !item.verifiable -> "·"
            item.ok -> "✓"
            else -> "✕"
        }
        appendLine("$mark ${item.label}：${item.detail}")
    }
}