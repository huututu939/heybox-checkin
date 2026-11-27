package com.heybox.checkin.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.heybox.checkin.R
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.ui.MainActivity

object Notifications {

    const val CHANNEL_ID = "checkin"

    /**
     * 连续失败用的第二条通道：普通通道是 LOW（无声），
     * 「连着几天没签上」必须能响一次，否则仍然是静默失败。
     */
    private const val WARN_CHANNEL_ID = "checkin_warn"
    private const val SERVICE_ID = 10
    private const val RESULT_ID = 11
    private const val RETRY_REQUEST = 42
    private const val LOGIN_REQUEST = 43

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "自动签到", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "每日签到进度与结果"
                },
            )
        }
        if (manager.getNotificationChannel(WARN_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(WARN_CHANNEL_ID, "连续失败提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "连续多天没签上时提醒一次"
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

    /**
     * 执行结果通知。
     *
     * @param hint 失败时给用户的下一步（来自 [com.heybox.checkin.data.Failure.hint]）；
     * @param streak 连续没签上的天数，≥2 时换成会响的通道并直接把天数写进标题；
     * @param kind 失败分类。登录过期时不再给「重试」（一定失败），直接给「重新登录」。
     */
    fun result(
        context: Context,
        success: Boolean,
        text: String,
        hint: String? = null,
        streak: Int = 0,
        kind: FailureKind? = null,
    ) {
        val session = kind == FailureKind.SESSION
        val warn = !success && !session && streak >= 2
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            RESULT_ID,
            NotificationCompat.Builder(context, if (warn) WARN_CHANNEL_ID else CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_checkin)
                .setContentTitle(
                    when {
                        success -> "今日签到完成"
                        session -> "登录已过期"
                        warn -> "连续 $streak 天没签上"
                        else -> "签到失败"
                    },
                )
                .setContentText(text)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        if (hint.isNullOrBlank()) text else "$text\n$hint",
                    ),
                )
                .setAutoCancel(true)
                .setContentIntent(if (session) loginIntent(context) else openApp(context))
                .apply {
                    if (session) addAction(0, "重新登录", loginIntent(context))
                    else if (!success) addAction(0, "立即重试", retryIntent(context))
                }
                .build(),
        )
    }

    /** 登录过期时直接落到登录页，少一步「打开 App → 找横幅」。 */
    private fun loginIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        LOGIN_REQUEST,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_LOGIN, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
