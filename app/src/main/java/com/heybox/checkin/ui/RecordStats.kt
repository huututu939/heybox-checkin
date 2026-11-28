package com.heybox.checkin.ui

import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.dayKey

/** 一个自然月的汇总：完成 / 跳过 / 失败，以及月末相对月初的 H 币增量。 */
internal data class MonthSummary(
    val done: Int,
    val skipped: Int,
    val failed: Int,
    val coinDelta: Long?,
)

internal fun monthSummary(days: List<WeekCell>): MonthSummary {
    val balances = days.map { it.coin }.filter { it > 0 }
    return MonthSummary(
        done = days.count { it.state == DayState.DONE },
        skipped = days.count { it.state == DayState.SKIPPED },
        failed = days.count { it.state == DayState.FAILED },
        // 只有一天有余额时算不出增量，宁可显示「—」也不编一个数
        coinDelta = if (balances.size >= 2) balances.last() - balances.first() else null,
    )
}

/** 分享成功率的统计窗口。历史能留 90 天，但成功率看最近 30 天更有意义。 */
internal const val SHARE_WINDOW_DAYS = 30

/**
 * 默认选中哪一天：优先当前月最近一条有记录的日子，其次当前月最后一天。
 * 月初当天还没记录时，不能落到上个月，否则可见日历里没有高亮、也没有明细。
 */
internal fun defaultDay(cells: List<WeekCell>, newestMonthKey: Int?): WeekCell? {
    val inMonth = cells.filter { it.monthKey == newestMonthKey }
    return inMonth.lastOrNull { it.state != DayState.NONE } ?: inMonth.lastOrNull()
}

/** 最近 30 天的分享任务：尝试次数 vs 最终 finish 的次数。 */
internal data class ShareSummary(
    val attempts: Int,
    val finished: Int,
) {
    val text: String get() = if (attempts == 0) "—" else "$finished/$attempts"
}

/**
 * 只统计真的尝试过的：`executed` 表示发了请求，`finish` 表示接口确认完成。
 * `waiting` 是开关没开或还没轮到的任务，不能算进成功率。
 */
internal fun shareSummary(
    history: List<RunRecord>,
    now: Long = System.currentTimeMillis(),
    days: Int = SHARE_WINDOW_DAYS,
): ShareSummary {
    // 一天内多跑一次会合并成一条记录，这里按天去重后的历史直接数即可
    var attempts = 0
    var finished = 0
    for (record in history) {
        if (now - record.at > days * 24L * 3600_000L) continue
        for (id in TaskParser.SHARE_IDS) {
            when (record.taskStates[id]) {
                "finish" -> {
                    attempts++
                    finished++
                }

                "executed" -> attempts++
            }
        }
    }
    return ShareSummary(attempts = attempts, finished = finished)
}

/** 漏签原因的一条：失败按 [FailureKind] 归并，「没跑」是整天没有任何记录的那类。 */
internal data class MissReason(val label: String, val count: Int)

/**
 * 一个月的漏签原因分布。
 *
 * 失败有分类可归并；更值得暴露的是「整天没跑」——闹钟被 ROM 清掉、后台被冻住
 * 都不会留下失败记录，日历上只是一格空白。App 装上之前的天数与今天（还没到点）
 * 都不算漏。
 */
internal fun missBreakdown(history: List<RunRecord>, days: List<WeekCell>): List<MissReason> {
    val firstDay = history.minOfOrNull { dayKey(it.at) } ?: return emptyList()
    val counts = LinkedHashMap<String, Int>()
    for (cell in days) {
        when (cell.state) {
            DayState.FAILED -> counts.merge(FailureKind.from(cell.errorKind).label, 1, Int::plus)

            DayState.NONE ->
                if (!cell.today && cell.year * 10000 + cell.month * 100 + cell.day >= firstDay) {
                    counts.merge("没跑", 1, Int::plus)
                }

            else -> Unit
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { MissReason(it.key, it.value) }
}
