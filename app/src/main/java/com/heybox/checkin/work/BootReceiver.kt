package com.heybox.checkin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.widget.Widgets

/**
 * 重启、应用更新、改时间 / 换时区后，闹钟都要重新落一次。
 *
 * 时间变更单独处理：随机时刻是按旧时区的窗口抽的，原样重落可能落到窗口外，
 * 所以这里重新抽一次（窗口已经过则按补签逻辑排到 1–3 分钟后）。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = SessionStore(context)
        val enabled = store.scheduledEnabled() && store.hasCredentials()
        val timeChanged = intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED

        if (enabled) {
            val stored = store.nextTrigger()
            when {
                // 改时间/换时区：原时刻按旧窗口抽的，重新抽签；但不要在这里补跑，
                // 否则会绕过当晚补签的网络与预算判定。错过的那次交给截止闹钟。
                timeChanged -> Scheduler.scheduleNext(context, catchUp = false)
                // 重启 / 覆盖安装会取消闹钟，但计划时间还在未来时不要重新抽签。
                stored > System.currentTimeMillis() -> Scheduler.rearm(context, stored)
                else -> Scheduler.scheduleNext(context)
            }
        }
        Widgets.refresh(context)
    }
}
