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
