package com.heybox.checkin.ui

import com.heybox.checkin.data.RunRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 「最近七天」每一格的状态：完成 / 跳过（当天已由其它入口完成）/ 没有记录。 */
enum class DayState { DONE, SKIPPED, FAILED, NONE }

data class WeekCell(
    val monthDay: String,
    /** 比如「11月」，记录页按月分组用。 */
    val monthLabel: String,
    val weekday: String,
    val state: DayState,
    val today: Boolean,
    val time: String?,
    val executed: Boolean,
    val expDelta: Long?,
    val coinDelta: Long,
    val batteryDelta: Long?,
    /** 失败时的原因，只有 FAILED 那格会有。 */
    val error: String? = null,
)

/**
 * 把历史记录摊成最近 [days] 天的格子，按时间正序（最早在前、今天在后）。
 * 纯函数，方便单测：跨天、跳过的判定都在这里。
 */
internal fun weekCells(history: List<RunRecord>, now: Long, days: Int = 7): List<WeekCell> {
    val base = Calendar.getInstance().apply { timeInMillis = now }
    val cells = ArrayList<WeekCell>(days)
    for (offset in days - 1 downTo 0) {
        val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        val millis = day.timeInMillis
        val record = history.firstOrNull { isSameLocalDay(it.at, millis) }
        cells += WeekCell(
            monthDay = formatMonthDay(millis),
            monthLabel = formatMonth(millis),
            weekday = weekdayShort(millis),
            state = when {
                record == null -> DayState.NONE
                !record.executed -> DayState.SKIPPED
                record.signed -> DayState.DONE
                else -> DayState.FAILED
            },
            today = offset == 0,
            time = record?.let { SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(it.at)) },
            executed = record?.executed == true,
            expDelta = record?.expDelta,
            coinDelta = record?.coinDelta ?: 0,
            batteryDelta = record?.batteryDelta,
            error = record?.error,
        )
    }
    return cells
}

private fun formatMonth(millis: Long): String =
    SimpleDateFormat("M月", Locale.CHINA).format(Date(millis))

private fun weekdayShort(millis: Long): String =
    SimpleDateFormat("E", Locale.CHINA).format(Date(millis)).removePrefix("周")
