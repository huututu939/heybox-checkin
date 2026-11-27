package com.heybox.checkin

import android.app.Application
import com.heybox.checkin.data.Diagnostics
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.work.Scheduler
import com.heybox.checkin.work.Watchdog

class HeyboxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashHandler(this)

        // 每次进程起来都确认值守兜底还在：JobScheduler 跨重启会恢复，
        // 但覆盖安装、清后台、部分 ROM 的省电策略会把它丢掉，这里幂等补一次。
        val store = SessionStore(this)
        if (store.scheduledEnabled() && store.hasCredentials()) {
            Watchdog.schedule(this)
            Scheduler.armDeadline(this)
        }
    }
}
