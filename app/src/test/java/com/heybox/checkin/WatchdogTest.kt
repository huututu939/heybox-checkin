package com.heybox.checkin

import com.heybox.checkin.work.Scheduler
import com.heybox.checkin.work.Watchdog
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 看门狗的判定必须保守：宁可少补一次，也不能在随机窗口内抢跑、或对已经失败的今天反复重试。
 */
class WatchdogTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun should(
        now: Long,
        nextTrigger: Long,
        ranToday: Boolean = false,
        hasRecordToday: Boolean = false,
    ) = Watchdog.shouldCatchUp(
        now = now,
        nextTrigger = nextTrigger,
        ranToday = ranToday,
        hasRecordToday = hasRecordToday,
        windowStartHour = 8,
    )

    @Test
    fun `窗口开始前不补`() {
        val now = at(2026, 11, 27, 7, 30)
        assertFalse(should(now, nextTrigger = 0L))
    }

    @Test
    fun `今天的闹钟还没到点时不抢跑`() {
        val now = at(2026, 11, 27, 10)
        assertFalse(should(now, nextTrigger = at(2026, 11, 27, 15)))
    }

    @Test
    fun `今天的闹钟已经过点还没记录就补`() {
        val now = at(2026, 11, 27, 16)
        assertTrue(should(now, nextTrigger = at(2026, 11, 27, 15)))
    }

    @Test
    fun `闹钟被排到明天但今天没记录也要补`() {
        val now = at(2026, 11, 27, 16)
        assertTrue(should(now, nextTrigger = at(2026, 11, 28, 10)))
    }

    @Test
    fun `没有排过闹钟时窗口内直接补`() {
        val now = at(2026, 11, 27, 10)
        assertTrue(should(now, nextTrigger = 0L))
    }

    @Test
    fun `昨天剩下的闹钟不算今天的`() {
        val now = at(2026, 11, 27, 10)
        assertTrue(should(now, nextTrigger = at(2026, 11, 26, 15)))
    }

    @Test
    fun `今天已经跑过就不补`() {
        val now = at(2026, 11, 27, 16)
        assertFalse(should(now, nextTrigger = at(2026, 11, 27, 15), ranToday = true))
    }

    @Test
    fun `今天已经失败过就交给重试预算`() {
        val now = at(2026, 11, 27, 16)
        assertFalse(should(now, nextTrigger = at(2026, 11, 27, 15), hasRecordToday = true))
    }

    @Test
    fun `截止检查排在窗口结束后十分钟`() {
        val now = at(2026, 11, 27, 10)
        assertEquals(at(2026, 11, 27, 20, 10), Scheduler.deadlineMillis(now, 8, 20))
    }

    @Test
    fun `窗口已过点时截止检查顺延到明天`() {
        val now = at(2026, 11, 27, 21)
        assertEquals(at(2026, 11, 28, 20, 10), Scheduler.deadlineMillis(now, 8, 20))
    }
}
