package com.heybox.checkin.data

import com.heybox.checkin.core.RunReport
import com.heybox.checkin.core.TaskSnapshot

/**
 * 最近一次执行的快照。
 *
 * 两类数字必须分开：
 * - `expDelta / coinDelta / batteryDelta`：这一次真正执行带来的增量（接口前后相减），首页「到账」用它；
 *   `expDelta == null` 表示升级跨了多级等无法可靠计算的情况，界面显示 `—` 而不是 +0；
 * - `exp / coin / battery / level / maxExp`：接口返回的账户状态（当前等级内经验进度、H 币余额、电池余额），只能当余额看。
 *
 * 第一次版本把后者当成了「今日收益」显示，才会出现 +90 与 +80 并存那种对不上的情况。
 */
data class RunRecord(
    val at: Long,
    val executed: Boolean,
    val expDelta: Long?,
    val coinDelta: Long,
    val batteryDelta: Long?,
    val exp: Long,
    val coin: Long,
    val battery: Long?,
    val level: Long?,
    val maxExp: Long?,
    val signed: Boolean,
    val taskStates: Map<String, String>,
) {
    companion object {
        fun from(snapshot: TaskSnapshot, signed: Boolean, report: RunReport? = null): RunRecord =
            RunRecord(
                at = System.currentTimeMillis(),
                executed = report != null,
                expDelta = report?.expDelta,
                coinDelta = report?.coinDelta ?: 0L,
                batteryDelta = report?.batteryDelta,
                exp = snapshot.exp,
                coin = snapshot.coin,
                battery = snapshot.battery,
                level = snapshot.level,
                maxExp = snapshot.maxExp,
                signed = signed,
                taskStates = snapshot.tasks.associate { it.id to it.state },
            )
    }

    /** 同一天再执行一次时，把两次的增量加起来，余额与任务状态用最新的。 */
    fun withAnotherRun(later: RunRecord): RunRecord = later.copy(
        executed = true,
        expDelta = sumNullable(expDelta, later.expDelta),
        coinDelta = coinDelta + later.coinDelta,
        batteryDelta = sumNullable(batteryDelta, later.batteryDelta),
    )

    /** 只是刷新了余额（本次没有执行，比如今天已被别的入口做完），保留原来的增量。 */
    fun withBalanceOnly(later: RunRecord): RunRecord = later.copy(
        executed = executed,
        expDelta = expDelta,
        coinDelta = coinDelta,
        batteryDelta = batteryDelta,
    )

    private fun sumNullable(a: Long?, b: Long?): Long? =
        if (a == null && b == null) null else (a ?: 0L) + (b ?: 0L)
}
