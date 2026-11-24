package com.heybox.checkin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.widget.Widgets

/** 重启或应用更新后闹钟会丢，重新排一次；顺便刷新桌面小组件上的「下次」。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SessionStore(context).scheduledEnabled()) Scheduler.scheduleNext(context)
        Widgets.refresh(context)
    }
}
