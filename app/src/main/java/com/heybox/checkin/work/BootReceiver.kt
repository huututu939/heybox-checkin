package com.heybox.checkin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.widget.Widgets

/** 重启或应用更新后闹钟会丢：有计划时间就原样重落，没有才重新抽签；顺便刷新桌面小组件。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = SessionStore(context)
        if (store.scheduledEnabled() && store.hasCredentials()) {
            val stored = store.nextTrigger()
            // 重启 / 覆盖安装会取消闹钟，但计划时间还在未来时不要重新抽签。
            if (stored > System.currentTimeMillis()) Scheduler.rearm(context, stored)
            else Scheduler.scheduleNext(context)
        }
        Widgets.refresh(context)
    }
}
