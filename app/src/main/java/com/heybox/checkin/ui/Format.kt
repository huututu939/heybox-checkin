package com.heybox.checkin.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun describe(error: Throwable): String =
    error.message?.takeIf { it.isNotBlank() }
        ?: error.cause?.message?.takeIf { it.isNotBlank() }
        ?: error.javaClass.simpleName

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))

internal fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(millis))

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

internal fun stageText(stage: String): String = when {
    stage == "inspect" -> "检查今日状态"
    stage == "sign" -> "正在签到"
    stage == "verify" -> "核对结果"
    stage.startsWith("share_") -> when (stage.removePrefix("share_")) {
        "1" -> "分享帖子"
        "19" -> "分享游戏"
        "31" -> "分享游戏评价"
        else -> "分享任务"
    }
    else -> stage
}
