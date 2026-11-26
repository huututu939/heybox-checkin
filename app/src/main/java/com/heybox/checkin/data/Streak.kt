package com.heybox.checkin.data

import java.util.Calendar

/**
 * 连续多少天没签上（失败，或整天没有任何记录）。
 *
 * 为什么要把「没有记录」也算进来：闹钟被 ROM 清掉、后台被冻住这类问题不会产生失败记录，
 * 用户能看到的只有「记录页空着」。如果只数失败，最严重的那种静默失效反而不会报警。
 *
 * 两条约束：
 * - 今天还没跑不算漏，不能早上八点就告诉用户「连续一天没签上」；
 * - 只数到第一条记录为止，新装的 App 不会一打开就顶着「连续 30 天没签上」。
 */
fun missStreak(
    history: List<RunRecord>,
    now: Long = System.currentTimeMillis(),
    maxDays: Int = SessionStore.HISTORY_DAYS,
): Int {
    if (history.isEmpty()) return 0
    val firstDay = history.minOf { dayKey(it.at) }
    var streak = 0
    val base = Calendar.getInstance().apply { timeInMillis = now }
    for (offset in 0..maxDays) {
        val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
        val millis = day.timeInMillis
        if (dayKey(millis) < firstDay) break
        val record = history.firstOrNull { dayKey(it.at) == dayKey(millis) }
        // signed = 真签上了；!executed = 今天已被别的入口做完，也算完成
        if (record != null && (record.signed || !record.executed)) break
        if (offset == 0 && record == null) continue
        streak++
    }
    return streak
}

/** 最近一次成功（含「其它入口已完成」）的时刻，没有任何记录时为 null。 */
fun lastSignedAt(history: List<RunRecord>): Long? =
    history.filter { it.signed || !it.executed }.maxOfOrNull { it.at }

private fun dayKey(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return calendar.get(Calendar.YEAR) * 10000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}
