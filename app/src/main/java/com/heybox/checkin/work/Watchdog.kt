package com.heybox.checkin.work

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import java.util.Calendar

/**
 * 值守看门狗：精确闹钟是主路径，但 force-stop、系统清理、部分 ROM 的后台管理会把
 * AlarmManager 里的闹钟直接扔掉，表现就是「整天没有记录」——而且要等用户下次打开 App
 * 才会被发现。这里补三层兜底：
 *
 * 1. 截止闹钟（[Scheduler.armDeadline]）：窗口结束后 10 分钟响一次，当天确实没跑就立刻补。
 *    它由精确闹钟广播触发，可以合法拉起前台服务（Android 12+ 的后台启动豁免）。
 * 2. 周期任务（[WatchdogJobService]，4 小时、可跨重启）：只负责把丢失的闹钟重新排上，
 *    不直接起服务——JobService 在后台没有拉起前台服务的豁免。
 * 3. 当晚补签：常规重试（2 次）用完、窗口也过了，但如果失败原因是网络/服务端/核对，
 *    且现在有网，再给一次机会。一天一次，避免把失败打成高频请求。
 */
object Watchdog {

    internal const val JOB_ID = 7
    private const val INTERVAL_MS = 4 * 3600_000L

    /** 值得在当晚再试一次的失败：登录、验证码、限流再试也没用。 */
    private val LATE_RETRY_KINDS = setOf(FailureKind.NETWORK, FailureKind.SERVER, FailureKind.VERIFY)

    fun schedule(context: Context) {
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, WatchdogJobService::class.java))
            .setPeriodic(INTERVAL_MS)
            .setPersisted(true)
            .build()
        runCatching { jobs(context).schedule(info) }
    }

    fun cancel(context: Context) {
        runCatching { jobs(context).cancel(JOB_ID) }
    }

    /**
     * 该不该补签。它同时被截止闹钟和周期任务调用，所以只做判断、不产生副作用。
     *
     * 已经失败过的今天不再补：失败会写一条记录，剩下的交给服务自己的重试预算，
     * 否则看门狗每 4 小时重试一次，等于把「失败」打成高频请求。当晚补签走 [shouldLateRetry]。
     */
    fun shouldCatchUp(
        now: Long,
        nextTrigger: Long,
        ranToday: Boolean,
        hasRecordToday: Boolean,
        windowStartHour: Int,
    ): Boolean {
        if (ranToday || hasRecordToday) return false
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, windowStartHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // 还没到窗口，不打扰用户设定的时段
        if (now < start) return false
        // 今天还有闹钟没到点：别抢在随机时刻之前执行，否则随机窗口就白设了
        if (nextTrigger > now && Scheduler.isSameLocalDay(nextTrigger, now)) return false
        return true
    }

    /**
     * 当晚补签的判定：今天失败过、失败类型值得再试、窗口已结束、现在有网。
     * 只做判断，预算由调用方扣。
     */
    fun shouldLateRetry(
        now: Long,
        record: RunRecord?,
        windowEnd: Long,
        hasNetwork: Boolean,
    ): Boolean {
        if (record == null || record.signed || !record.executed) return false
        if (!Scheduler.isSameLocalDay(record.at, now)) return false
        if (now < windowEnd) return false
        if (!hasNetwork) return false
        return FailureKind.from(record.errorKind) in LATE_RETRY_KINDS
    }

    /** 截止闹钟用：条件成立就直接拉起服务，能补的当场补。 */
    fun catchUp(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (shouldRun(context, now)) return start(context)
        if (!shouldLateRetry(context, now)) return false
        // 预算先扣再起服务：起服务失败也不该在下一个 4 小时窗口再打一次
        if (!SessionStore(context).takeLateRetry()) return false
        return start(context)
    }

    /** 周期任务用：不直接起服务，把下一次排到 1 分钟后，由闹钟去拉起。 */
    fun repairAlarm(context: Context, now: Long = System.currentTimeMillis()) {
        if (shouldRun(context, now)) {
            Scheduler.scheduleNext(context, now = now)
            return
        }
        if (!shouldLateRetry(context, now)) return
        val store = SessionStore(context)
        if (!store.takeLateRetry()) return
        val at = now + 60_000L
        val ok = Scheduler.schedule(context, at)
        store.setNextTrigger(if (ok) at else 0L)
    }

    private fun start(context: Context): Boolean = runCatching {
        CheckInService.start(context)
        true
    }.getOrElse {
        Notifications.result(context, false, "系统拒绝后台启动，补签没跑成；打开 App 手动执行一次")
        false
    }

    private fun shouldRun(context: Context, now: Long): Boolean {
        val store = SessionStore(context)
        if (!store.scheduledEnabled() || !store.hasCredentials()) return false
        val record = store.loadRun()?.takeIf { Scheduler.isSameLocalDay(it.at, now) }
        return shouldCatchUp(
            now = now,
            nextTrigger = store.nextTrigger(),
            ranToday = store.ranToday(),
            hasRecordToday = record != null,
            windowStartHour = store.windowStart(),
        )
    }

    private fun shouldLateRetry(context: Context, now: Long): Boolean {
        val store = SessionStore(context)
        if (!store.scheduledEnabled() || !store.hasCredentials()) return false
        return shouldLateRetry(
            now = now,
            record = store.loadRun(),
            windowEnd = Scheduler.todayWindowEnd(now, store.windowStart(), store.windowEnd()),
            hasNetwork = hasNetwork(context),
        )
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun jobs(context: Context) =
        context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
}
