package com.heybox.checkin.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heybox.checkin.data.SessionStore

class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = SessionStore(context)
        if (!store.scheduledEnabled()) return
        // 凭据被清掉（退出登录 / 重登中）时不要空跑一次再报失败；等重新登录后首页重排。
        if (!store.hasCredentials()) return

        try {
            // 长流程（几十秒到两分钟）必须靠前台服务承载；在接收器里硬跑会被系统按 ANR 掐断。
            CheckInService.start(context)
        } catch (e: Exception) {
            // Android 12+ 少数机型拒绝后台起前台服务：给明确提示并按预算稍后重试，不在接收器里执行任务。
            Notifications.result(context, false, "系统拒绝后台启动，稍后会再试一次；反复出现请打开 App 手动执行")
            store.setLastResult("系统拒绝后台启动：${e.javaClass.simpleName}")
            if (store.takeRetry()) {
                val at = System.currentTimeMillis() + 10 * 60_000L
                val ok = Scheduler.schedule(context, at)
                store.setNextTrigger(if (ok) at else 0L)
            }
        }
    }
}
