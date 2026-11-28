package com.heybox.checkin

import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.ui.DayState
import com.heybox.checkin.ui.WeekCell
import com.heybox.checkin.ui.defaultDay
import com.heybox.checkin.ui.missBreakdown
import com.heybox.checkin.ui.monthSummary
import com.heybox.checkin.ui.shareSummary
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordStatsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 10)
        }.timeInMillis

    private fun cell(
        state: DayState,
        coin: Long = 0,
        day: Int = 1,
        month: Int = 11,
        errorKind: String? = null,
        today: Boolean = false,
    ) = WeekCell(
        monthDay = "$month/$day",
        monthLabel = "${month}月",
        weekday = "一",
        state = state,
        today = today,
        time = null,
        executed = state == DayState.DONE || state == DayState.FAILED,
        expDelta = null,
        coinDelta = 0,
        coin = coin,
        batteryDelta = null,
        error = null,
        errorKind = errorKind,
        year = 2026,
        month = month,
        day = day,
    )

    private fun record(
        millis: Long,
        tasks: Map<String, String>,
        signed: Boolean = true,
    ) = RunRecord(
        at = millis,
        executed = true,
        expDelta = 90,
        coinDelta = 300,
        batteryDelta = 1,
        exp = 170,
        coin = 1000,
        battery = 5,
        level = 3,
        maxExp = 200,
        signed = signed,
        taskStates = tasks,
    )

    @Test
    fun `月度汇总按状态计数`() {
        val days = listOf(
            cell(DayState.DONE, coin = 500, day = 1),
            cell(DayState.SKIPPED, coin = 500, day = 2),
            cell(DayState.FAILED, coin = 0, day = 3),
            cell(DayState.NONE, day = 4),
            cell(DayState.DONE, coin = 800, day = 5),
        )
        val summary = monthSummary(days)
        assertEquals(2, summary.done)
        assertEquals(1, summary.skipped)
        assertEquals(1, summary.failed)
        // 月初余额 500，月末 800
        assertEquals(300L, summary.coinDelta)
    }

    @Test
    fun `只有一天有余额时算不出月增量`() {
        val days = listOf(cell(DayState.DONE, coin = 500), cell(DayState.NONE, day = 2))
        assertNull(monthSummary(days).coinDelta)
    }

    @Test
    fun `分享统计区分尝试与完成`() {
        val now = at(2026, 11, 25, 14)
        val history = listOf(
            record(now, mapOf("sign" to "finish", "1" to "finish", "19" to "executed")),
            record(now - 86_400_000L, mapOf("1" to "finish", "31" to "waiting")),
        )
        val share = shareSummary(history, now)
        // 第一次：帖子 finish + 游戏 executed；第二次：帖子 finish
        assertEquals(3, share.attempts)
        assertEquals(2, share.finished)
        assertEquals("2/3", share.text)
    }

    @Test
    fun `没有尝试过分享时不显示成功率`() {
        val now = at(2026, 11, 25, 14)
        val history = listOf(record(now, mapOf("sign" to "finish", "1" to "waiting")))
        val share = shareSummary(history, now)
        assertEquals(0, share.attempts)
        assertEquals("—", share.text)
    }

    @Test
    fun `分享统计只看最近 30 天`() {
        val now = at(2026, 11, 25, 14)
        val history = listOf(
            record(now - 100 * 86_400_000L, mapOf("1" to "finish")),
            record(now - 40 * 86_400_000L, mapOf("19" to "finish")),
            record(now - 20 * 86_400_000L, mapOf("31" to "finish")),
        )
        // 只有 20 天前那条在窗口内
        assertEquals(1, shareSummary(history, now).attempts)
    }

    @Test
    fun `月初没有记录时默认选中当前月而不是上月`() {
        val cells = listOf(
            cell(DayState.DONE, day = 31, month = 10),
            cell(DayState.NONE, day = 1, month = 11),
        )
        val selected = defaultDay(cells, newestMonthKey = 202611)
        assertEquals(1, selected?.day)
        assertEquals(11, selected?.month)
    }

    @Test
    fun `默认选中当前月最近一个有记录的日子`() {
        val cells = listOf(
            cell(DayState.DONE, day = 30, month = 10),
            cell(DayState.DONE, day = 1, month = 11),
            cell(DayState.NONE, day = 2, month = 11),
            cell(DayState.DONE, day = 3, month = 11),
            cell(DayState.NONE, day = 4, month = 11),
        )
        assertEquals(3, defaultDay(cells, 202611)?.day)
    }

    @Test
    fun `窗口外的记录不计入分享统计`() {
        val now = at(2026, 11, 25, 14)
        // 历史保留 90 天，这里取 100 天前
        val history = listOf(record(now - 100 * 86_400_000L, mapOf("1" to "finish")))
        assertEquals(0, shareSummary(history, now).attempts)
    }

    @Test
    fun `漏签原因按分类归并`() {
        val history = listOf(record(at(2026, 11, 5), mapOf("sign" to "finish")))
        val cells = listOf(
            cell(DayState.DONE, day = 5),
            cell(DayState.FAILED, day = 6, errorKind = "network"),
            cell(DayState.FAILED, day = 7, errorKind = "network"),
            cell(DayState.FAILED, day = 8, errorKind = "server"),
            cell(DayState.NONE, day = 9),
            cell(DayState.NONE, day = 10),
            cell(DayState.NONE, day = 11, today = true),
        )
        val miss = missBreakdown(history, cells)
        assertEquals(3, miss.size)
        assertEquals("没跑", miss[0].label)
        assertEquals(2, miss[0].count)
        // 数量并列时按名称码点排序：没跑 < 网络
        assertEquals("网络", miss[1].label)
        assertEquals(2, miss[1].count)
        assertEquals("服务", miss[2].label)
        assertEquals(1, miss[2].count)
    }

    @Test
    fun `装上之前的日子不算没跑`() {
        val history = listOf(record(at(2026, 11, 10), mapOf("sign" to "finish")))
        val cells = listOf(
            cell(DayState.NONE, day = 8),
            cell(DayState.NONE, day = 11),
            cell(DayState.NONE, day = 12, today = true),
        )
        val miss = missBreakdown(history, cells)
        assertEquals(1, miss.size)
        assertEquals("没跑", miss[0].label)
        assertEquals(1, miss[0].count)
    }

    @Test
    fun `没有历史时漏签分布为空`() {
        assertEquals(0, missBreakdown(emptyList(), listOf(cell(DayState.FAILED, day = 3))).size)
    }
}
