package com.heybox.checkin.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.heybox.checkin.R
import com.heybox.checkin.ui.MainActivity
import com.heybox.checkin.work.CheckInService
import org.json.JSONObject

/**
 * 桌面小组件。用 RemoteViews 而不是 Glance：不引新依赖，系统桌面直接托管绘制，
 * 也顺带把进程重要性往上抬一档（有 widget 的包被回收得更晚）。
 */
object Widgets {

    private const val PREFS = "heybox_session"
    private const val KEY_RECORD = "run_record"
    private const val KEY_SCHEDULED = "scheduled"
    private const val KEY_NEXT = "next_trigger"
    private const val KEY_LOGIN = "heybox_id"
    private const val KEY_EXPIRED = "session_expired"

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val targets = listOf(
            Widget4x1Provider::class.java to R.layout.widget_4x1,
            Widget2x2Provider::class.java to R.layout.widget_2x2,
        )
        for ((provider, layout) in targets) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) continue
            for (id in ids) manager.updateAppWidget(id, build(context, layout))
        }
    }

    fun build(context: Context, layout: Int): RemoteViews {
        val data = read(context)
        val views = RemoteViews(context.packageName, layout)
        if (layout == R.layout.widget_4x1) {
            views.setTextViewText(R.id.widget_status, data.line)
        } else {
            views.setTextViewText(R.id.widget_badge, data.badge)
            views.setTextViewText(R.id.widget_delta, data.delta)
            views.setTextViewText(R.id.widget_next, data.next)
        }
        if (data.loginRequired) {
            // 登录态没了，按钮不能再拉起签到服务：改成「去登录」，点开 App。
            views.setTextViewText(R.id.widget_action, "去登录")
            views.setOnClickPendingIntent(R.id.widget_action, openIntent(context))
        } else {
            views.setOnClickPendingIntent(R.id.widget_action, runIntent(context))
        }
        views.setOnClickPendingIntent(R.id.widget_open, openIntent(context))
        return views
    }

    private fun read(context: Context): WidgetData {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECORD, null)
        val record = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
        val hasLogin = prefs.getString(KEY_LOGIN, null) != null
        val expired = prefs.getBoolean(KEY_EXPIRED, false)
        return describeWidget(
            record = record,
            scheduled = prefs.getBoolean(KEY_SCHEDULED, false),
            nextAt = prefs.getLong(KEY_NEXT, 0L),
            now = System.currentTimeMillis(),
            loginRequired = !hasLogin || expired,
        )
    }

    private fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 1, intent, flags())
    }

    /** 组件上的按钮直接拉起前台服务，走「立即重试」同一条路径，带 force 标记。 */
    private fun runIntent(context: Context): PendingIntent {
        val intent = Intent(context, CheckInService::class.java)
            .setAction(CheckInService.ACTION_RETRY)
            .putExtra(CheckInService.EXTRA_FORCE, true)
        return PendingIntent.getForegroundService(context, 2, intent, flags())
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

class Widget4x1Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val views = Widgets.build(context, R.layout.widget_4x1)
        for (id in ids) manager.updateAppWidget(id, views)
    }
}

class Widget2x2Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val views = Widgets.build(context, R.layout.widget_2x2)
        for (id in ids) manager.updateAppWidget(id, views)
    }
}
