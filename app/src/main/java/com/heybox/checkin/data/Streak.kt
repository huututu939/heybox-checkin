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

/**
 * 连续签到天数：从今天往回数，完成（含「其它入口已完成」）的连续天数。
 *
 * 今天还没记录不算断，从昨天开始数；失败或整天没记录即中断。
 * 历史只留 [SessionStore.HISTORY_DAYS] 天，所以最多也只数到这里。
 */
fun signStreak(
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
        val signed = record != null && (record.signed || !record.executed)
        if (!signed) {
            // 今天的随机时刻可能还没到，没记录不代表断
            if (offset == 0 && record == null) continue
            break
        }
        streak++
    }
    return streak
}

/** 「年 * 10000 + 月 * 100 + 日」的当天 key；漏签统计、备份合并共用。 */
internal fun dayKey(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return calendar.get(Calendar.YEAR) * 10000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}

/** 连签里程碑。挑 7 是让新用户先拿到一个够得着的，180/365 是给长期驻守的目标。 */
internal val STREAK_MILESTONES = listOf(7, 30, 90, 180, 365)

/** 下一个未达成的里程碑：目标天数与还差几天；全部达成后为 null。 */
internal fun nextMilestone(streak: Int): Pair<Int, Int>? =
    STREAK_MILESTONES.firstOrNull { it > streak }?.let { it to (it - streak) }
