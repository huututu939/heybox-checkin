package com.heybox.checkin.work

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * 周期任务只做一件事：发现今天的闹钟丢了就重新排一次（[Watchdog.repairAlarm]）。
 *
 * 不在 JobService 里直接 startForegroundService：Android 12+ 的后台启动限制不豁免
 * JobScheduler，直接起会抛 ForegroundServiceStartNotAllowedException。
 */
class WatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Watchdog.repairAlarm(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
