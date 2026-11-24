package com.heybox.checkin.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.heybox.checkin.R
import com.heybox.checkin.ui.MainActivity

object Notifications {

    const val CHANNEL_ID = "checkin"
    private const val SERVICE_ID = 10
    private const val RESULT_ID = 11
    private const val RETRY_REQUEST = 42

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "自动签到", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "每日签到进度与结果"
                },
            )
        }
    }

    fun serviceNotification(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle("盒签正在执行")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(context))
            .build()

    fun updateService(context: Context, text: String) {
        context.getSystemService(NotificationManager::class.java).notify(SERVICE_ID, serviceNotification(context, text))
    }

    fun result(context: Context, success: Boolean, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            RESULT_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_checkin)
                .setContentTitle(if (success) "今日签到完成" else "签到失败")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .apply {
                    if (!success) addAction(0, "立即重试", retryIntent(context))
                }
                .build(),
        )
    }

    /** 失败通知上的「立即重试」：直接拉起前台服务，跳过「今天已完成」的拦截。 */
    private fun retryIntent(context: Context): PendingIntent {
        val intent = Intent(context, CheckInService::class.java)
            .setAction(CheckInService.ACTION_RETRY)
            .putExtra(CheckInService.EXTRA_FORCE, true)
        return PendingIntent.getForegroundService(
            context,
            RETRY_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
