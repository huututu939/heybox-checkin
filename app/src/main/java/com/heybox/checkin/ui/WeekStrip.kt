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
    /** 当天的 H 币余额，月度走势用它算增量。 */
    val coin: Long = 0L,
    val batteryDelta: Long?,
    /** 失败时的原因，只有 FAILED 那格会有。 */
    val error: String? = null,
    /** 失败分类，见 [com.heybox.checkin.data.FailureKind]。 */
    val errorKind: String? = null,
    /** 月历排版用，月从 1 开始。 */
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
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
            coin = record?.coin ?: 0L,
            batteryDelta = record?.batteryDelta,
            error = record?.error,
            errorKind = record?.errorKind,
            year = day.get(Calendar.YEAR),
            month = day.get(Calendar.MONTH) + 1,
            day = day.get(Calendar.DAY_OF_MONTH),
        )
    }
    return cells
}

/** 一个自然月在月历里的排版：[cells] 前面补 null 占位，让 1 号落在正确的星期几。 */
internal data class MonthGrid(
    val label: String,
    val year: Int,
    val month: Int,
    val cells: List<WeekCell?>,
) {
    val days: List<WeekCell> get() = cells.filterNotNull()
}

/**
 * 把最近 [days] 天按自然月切开，新的月份在前。
 * 前置占位按「周一在第一列」算，和图例表头保持一致。
 */
internal fun monthGrids(history: List<RunRecord>, now: Long, days: Int = 30): List<MonthGrid> {
    val cells = weekCells(history, now, days)
    val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    val groups = LinkedHashMap<Int, MutableList<WeekCell>>()
    for (cell in cells) groups.getOrPut(cell.year * 100 + cell.month) { mutableListOf() } += cell
    return groups.values.map { list ->
        val first = list.first()
        MonthGrid(
            label = if (first.year == thisYear) "${first.month}月" else "${first.year}年${first.month}月",
            year = first.year,
            month = first.month,
            cells = List(mondayIndex(first.year, first.month, first.day)) { null } + list,
        )
    }.reversed()
}

/** 星期几在「周一排第一」的七列里占第几格（0 = 周一）。 */
internal fun mondayIndex(year: Int, month: Int, day: Int): Int {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day)
    }
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun formatMonth(millis: Long): String = SimpleDateFormat("M月", Locale.CHINA).format(Date(millis))

private fun weekdayShort(millis: Long): String =
    SimpleDateFormat("E", Locale.CHINA).format(Date(millis)).removePrefix("周")
