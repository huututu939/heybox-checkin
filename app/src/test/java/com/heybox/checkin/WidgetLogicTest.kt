package com.heybox.checkin

import com.heybox.checkin.widget.describeWidget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 小组件文案的跨天 / 过期判定。 */
class WidgetLogicTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun record(atMs: Long, signed: Boolean, executed: Boolean = false) = JSONObject()
        .put("at", atMs)
        .put("signed", signed)
        .put("executed", executed)
        .put("exp_delta", 12L)
        .put("coin_delta", 5L)

    @Test
    fun todaySignedShowsDone() {
        val now = at(2026, 11, 24, 18, 0)
        val data = describeWidget(record(now, signed = true), scheduled = true, nextAt = 0L, now = now, loginRequired = false)
        assertEquals("已签到", data.badge)
        assertEquals("今日已完成", data.delta)
    }

    @Test
    fun yesterdayRecordBecomesStaleAfterMidnight() {
        val now = at(2026, 11, 24, 0, 5)
        val yesterday = at(2026, 11, 23, 21, 30)
        val data = describeWidget(record(yesterday, signed = true), scheduled = false, nextAt = 0L, now = now, loginRequired = false)
        assertEquals("未同步", data.badge)
        assertEquals("打开 App 同步", data.delta)
        assertEquals("未同步 · 定时未开启", data.line)
    }

    @Test
    fun expiredSessionBlocksCheckInCopy() {
        val now = at(2026, 11, 24, 9, 0)
        val data = describeWidget(record(now, signed = true), scheduled = true, nextAt = now + 3600_000, now = now, loginRequired = true)
        assertTrue(data.loginRequired)
        assertEquals("待登录", data.badge)
        assertEquals("重新登录后恢复定时", data.next)
        assertTrue(data.line.contains("登录已过期"))
    }

    @Test
    fun scheduledTimeUsesHourMinuteToday() {
        val now = at(2026, 11, 24, 9, 0)
        val next = at(2026, 11, 24, 14, 37)
        val data = describeWidget(null, scheduled = true, nextAt = next, now = now, loginRequired = false)
        assertEquals("下次 14:37", data.next)
    }

    @Test
    fun nextTriggerTomorrowIncludesDate() {
        val now = at(2026, 11, 24, 20, 0)
        val next = at(2026, 11, 25, 8, 15)
        val data = describeWidget(null, scheduled = true, nextAt = next, now = now, loginRequired = false)
        assertEquals("下次 11/25 08:15", data.next)
    }

    @Test
    fun signedWithEnabledSharePendingIsNotFullyDone() {
        val now = at(2026, 11, 24, 12, 0)
        val record = record(now, signed = true).put(
            "tasks",
            JSONObject().put("1", "waiting").put("19", "finish").put("31", "finish"),
        )
        val data = describeWidget(
            record = record,
            scheduled = true,
            nextAt = 0L,
            now = now,
            loginRequired = false,
            enabledShares = setOf("1"),
        )
        assertEquals("已签到 · 分享待完成", data.delta)
    }

    @Test
    fun scheduledButUnscheduledAlarmIsNotShownAsOff() {
        val now = at(2026, 11, 24, 9, 0)
        val data = describeWidget(null, scheduled = true, nextAt = 0L, now = now, loginRequired = false)
        assertEquals("定时已开启 · 尚未排定", data.next)
        assertEquals("未同步 · 尚未排定", data.line)
    }

    @Test
    fun `失败时小组件显示原因`() {
        val now = System.currentTimeMillis()
        val raw = JSONObject()
            .put("at", now)
            .put("executed", true)
            .put("signed", false)
            .put("error", "登录已过期")
        val data = describeWidget(raw, scheduled = true, nextAt = now + 3600_000, now = now, loginRequired = false)
        assertEquals("失败", data.badge)
        assertTrue(data.delta.contains("登录已过期"))
    }
}
