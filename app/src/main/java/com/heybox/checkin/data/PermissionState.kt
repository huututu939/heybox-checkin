package com.heybox.checkin.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.heybox.checkin.work.Notifications

/**
 * 权限状态的唯一来源：诊断、自检、首页都读这里，避免三处各写一份后慢慢漂移。
 * 只读系统状态，不申请权限。
 */
object PermissionState {

    /** 通知是否真的能发出来；返回 null 表示正常，否则是给用户看的原因。 */
    fun notificationBlockedReason(context: Context): String? {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return "系统未允许通知"
        }
        // 应用级开着、但「自动签到」渠道被单独关掉时，签到结果仍然发不出来
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(Notifications.CHANNEL_ID)
        return if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            "签到通知渠道被关闭"
        } else {
            null
        }
    }

    fun notificationsAllowed(context: Context): Boolean = notificationBlockedReason(context) == null

    fun batteryAllowed(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun exactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }
}