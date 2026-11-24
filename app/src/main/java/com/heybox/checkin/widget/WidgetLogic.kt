package com.heybox.checkin.widget

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 小组件文案的纯函数部分：不碰 Context / RemoteViews，方便 JVM 单测。
 * 「跨天」靠 now 重新计算日期，而不是缓存上一次结果。
 */
internal data class WidgetData(
    val line: String,
    val badge: String,
    val delta: String,
    val next: String,
    val loginRequired: Boolean = false,
)

internal fun describeWidget(
    record: JSONObject?,
    scheduled: Boolean,
    nextAt: Long,
    now: Long,
    loginRequired: Boolean,
    enabledShares: Set<String> = emptySet(),
): WidgetData {
    if (loginRequired) {
        return WidgetData(
            line = "登录已过期 · 点击重新登录",
            badge = "待登录",
            delta = "登录态失效，签到已暂停",
            next = "重新登录后恢复定时",
            loginRequired = true,
        )
    }

    val today = record != null && isSameDay(record.optLong("at", 0L), now)
    val signed = today && record?.optBoolean("signed", false) == true
    val executed = today && record?.optBoolean("executed", false) == true
    val expDelta = record?.takeIf { it.has("exp_delta") }?.optLong("exp_delta")
    val coinDelta = record?.optLong("coin_delta", 0L) ?: 0L
    val tasks = record?.optJSONObject("tasks")
    // 只有开启的分享任务都结算了，才算「今日已完成」；全关时签到完就算完成。
    val shareDone = enabledShares.all { tasks?.optString(it) == "finish" }

    val badge = when {
        signed -> "已签到"
        today -> "待签到"
        else -> "未同步"
    }
    val delta = when {
        executed && expDelta != null -> "+$expDelta 经验 · +$coinDelta H 币"
        executed -> "经验 — · +$coinDelta H 币"
        signed && shareDone -> "今日已完成"
        signed -> "已签到 · 分享待完成"
        today -> "今天还没签到"
        else -> "打开 App 同步"
    }
    val next = when {
        !scheduled -> "定时未开启"
        nextAt <= 0L -> "定时已开启 · 尚未排定"
        else -> "下次 ${shortTime(nextAt, now)}"
    }
    val status = if (signed) "已签到" else if (today) "待签到" else "未同步"
    val schedule = when {
        !scheduled -> "定时未开启"
        nextAt <= 0L -> "尚未排定"
        else -> "下次 ${shortTime(nextAt, now)}"
    }
    return WidgetData(line = "$status · $schedule", badge = badge, delta = delta, next = next)
}

internal fun isSameDay(ms: Long, now: Long): Boolean {
    if (ms <= 0L) return false
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

internal fun shortTime(ms: Long, now: Long): String {
    val pattern = if (isSameDay(ms, now)) "HH:mm" else "M/d HH:mm"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(ms))
}
