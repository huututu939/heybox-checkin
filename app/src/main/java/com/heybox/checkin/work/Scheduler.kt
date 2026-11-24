package com.heybox.checkin.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.heybox.checkin.data.SessionStore
import java.util.Calendar
import kotlin.random.Random

/**
 * 每天在随机时间窗口内挑一个时刻执行，避免「每天同一分钟」这种一眼能看出来的机器特征。
 * 默认窗口 08:00–20:00，可在 SessionStore 里改。每次跑完立刻按新随机时刻排下一次。
 */
object Scheduler {

    const val ACTION_RUN = "com.heybox.checkin.RUN_DAILY"
    private const val REQUEST_CODE = 1001
    private const val DAY_MS = 24L * 3600 * 1000

    /**
     * 关键规则：只在「今天窗口里还没过去的剩余时间」里随机，绝不因为随机点比当前时间早
     * 就把任务推到明天（那会静默漏掉今天）。今天窗口已过才落到明天。
     */
    fun nextTriggerMillis(
        now: Long = System.currentTimeMillis(),
        startHour: Int = SessionStore.DEFAULT_START_HOUR,
        endHour: Int = SessionStore.DEFAULT_END_HOUR,
        random: Random = Random.Default,
    ): Long {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(start + 1, 24)
        val windowMs = (end - start) * 3600_000L

        val dayStart = windowStartToday(now, start)
        val dayEnd = dayStart + windowMs
        val from = maxOf(now + 1000L, dayStart)

        if (from >= dayEnd) {
            // 今天窗口已经过完 → 在明天窗口里随机
            return dayStart + DAY_MS + random.nextLong(0, windowMs)
        }
        return from + random.nextLong(0, dayEnd - from)
    }

    /** 今天窗口的结束时刻，用于把下一次强制排到明天。 */
    fun todayWindowEnd(
        now: Long = System.currentTimeMillis(),
        startHour: Int = SessionStore.DEFAULT_START_HOUR,
        endHour: Int = SessionStore.DEFAULT_END_HOUR,
    ): Long {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(start + 1, 24)
        return windowStartToday(now, start) + (end - start) * 3600_000L
    }

    /**
     * 读取窗口，随机算下一次，落闹钟并记住时间用于界面展示。
     *
     * @param skipToday 今天已经跑过（成功/无需再跑），必须排到明天，不能在今天再排一次。
     * @param catchUp 今天没跑且窗口已过时，是否立刻补一次。失败重试时要关掉，否则会每几分钟循环重试。
     */
    fun scheduleNext(
        context: Context,
        skipToday: Boolean = false,
        catchUp: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val store = SessionStore(context)
        val start = store.windowStart()
        val end = store.windowEnd()

        var at = nextTriggerMillis(now, start, end)
        val sameDay = isSameLocalDay(at, now)
        if (skipToday || store.ranToday()) {
            if (sameDay) at = nextTriggerMillis(todayWindowEnd(now, start, end), start, end)
        } else if (!sameDay && catchUp) {
            // 今天还没跑，但今天的随机窗口已经过完 → 补一次，1–3 分钟后执行
            at = now + 60_000L + Random.nextLong(0, 120_000L)
        }

        val ok = schedule(context, at)
        // 闹钟排不上（极少数 ROM 同时拒绝精确与兜底闹钟）时不要谎报「已排定」。
        store.setNextTrigger(if (ok) at else 0L)
        return if (ok) at else 0L
    }

    fun schedule(context: Context, atMillis: Long): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        return runCatching {
            if (exact) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }.recoverCatching {
            // 精确闹钟被拒时退回非精确，至少别把这次执行整段丢掉。
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }.isSuccess
    }

    /**
     * 按 prefs 里已存的时刻重新落一次闹钟。
     *
     * force-stop、系统清理、部分 ROM 的后台管理会直接取消 AlarmManager 里的闹钟，
     * 但 `next_trigger` 仍然留在未来。冷启动时如果只信这个值，就会出现「界面显示已排定、
     * 实际没有闹钟」。这里用同一个时刻重新落一次，不重新随机，幂等。
     */
    fun rearm(context: Context, atMillis: Long): Boolean {
        val ok = schedule(context, atMillis)
        SessionStore(context).setNextTrigger(if (ok) atMillis else 0L)
        return ok
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context))
        SessionStore(context).setNextTrigger(0)
    }

    fun isSameLocalDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun windowStartToday(now: Long, startHour: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CheckInReceiver::class.java).setAction(ACTION_RUN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
