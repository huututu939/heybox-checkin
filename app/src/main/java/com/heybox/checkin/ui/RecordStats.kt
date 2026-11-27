package com.heybox.checkin.ui

import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore

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
    days: Int = SessionStore.HISTORY_DAYS,
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
