package com.heybox.checkin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.heybox.checkin.data.RunRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun describe(error: Throwable): String =
    error.message?.takeIf { it.isNotBlank() }
        ?: error.cause?.message?.takeIf { it.isNotBlank() }
        ?: error.javaClass.simpleName

/** 打开外部链接；没有可用的浏览器时用 Toast 兜底，不让调用方崩。 */
internal fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show() }
}

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))

internal fun formatDay(millis: Long): String =
    SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(millis))

internal fun formatMonthDay(millis: Long): String =
    SimpleDateFormat("M/d", Locale.CHINA).format(Date(millis))

internal fun hourLabel(hour: Int): String = String.format(Locale.CHINA, "%02d:00", hour)

internal fun isSameLocalDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

/**
 * 只有同一天的快照才能当「今天」用。
 * 不这么过滤，跨天之后首页会把昨天的完成态、任务勾选和增量都当成今天的。
 */
internal fun RunRecord?.todayOrNull(now: Long): RunRecord? =
    this?.takeIf { isSameLocalDay(it.at, now) }
