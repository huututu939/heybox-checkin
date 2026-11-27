package com.heybox.checkin

import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
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

    private fun failed(millis: Long, kind: FailureKind) =
        RunRecord.failure(millis, "测试失败", kind)

    private fun late(
        record: RunRecord?,
        now: Long,
        network: Boolean = true,
        pendingRetry: Boolean = false,
    ) = Watchdog.shouldLateRetry(
        now = now,
        record = record,
        windowEnd = at(2026, 11, 27, 20),
        hasNetwork = network,
        pendingRetry = pendingRetry,
    )

    @Test
    fun `窗口结束后网络类失败可以再补一次`() {
        val now = at(2026, 11, 27, 20, 30)
        assertTrue(late(failed(at(2026, 11, 27, 14), FailureKind.NETWORK), now))
    }

    @Test
    fun `窗口还没结束时不越过重试预算`() {
        val now = at(2026, 11, 27, 18)
        assertFalse(late(failed(at(2026, 11, 27, 14), FailureKind.NETWORK), now))
    }

    @Test
    fun `常规重试还排着时不再叠加当晚补签`() {
        val now = at(2026, 11, 27, 20, 30)
        val record = failed(at(2026, 11, 27, 19, 55), FailureKind.NETWORK)
        // 20:15 还有一次常规重试，截止检查要等它
        assertFalse(late(record, now, pendingRetry = true))
    }

    @Test
    fun `常规重试已经过点就允许当晚补签`() {
        val now = at(2026, 11, 27, 20, 30)
        val record = failed(at(2026, 11, 27, 14), FailureKind.NETWORK)
        assertTrue(late(record, now, pendingRetry = false))
    }

    @Test
    fun `没有网络时不当晚补签`() {
        val now = at(2026, 11, 27, 20, 30)
        assertFalse(late(failed(at(2026, 11, 27, 14), FailureKind.NETWORK), now, network = false))
    }

    @Test
    fun `登录和验证码失败不再补`() {
        val now = at(2026, 11, 27, 20, 30)
        assertFalse(late(failed(at(2026, 11, 27, 14), FailureKind.SESSION), now))
        assertFalse(late(failed(at(2026, 11, 27, 14), FailureKind.CAPTCHA), now))
        assertFalse(late(failed(at(2026, 11, 27, 14), FailureKind.RATE_LIMIT), now))
    }

    @Test
    fun `今天已签到就不补`() {
        val now = at(2026, 11, 27, 20, 30)
        val done = failed(at(2026, 11, 27, 14), FailureKind.NETWORK).copy(signed = true)
        assertFalse(late(done, now))
        // 「其它入口已完成」同理：没有执行，也不该再签
        assertFalse(late(done.copy(signed = false, executed = false), now))
    }

    @Test
    fun `昨天的失败不会拖到今天补`() {
        val now = at(2026, 11, 27, 20, 30)
        assertFalse(late(failed(at(2026, 11, 26, 14), FailureKind.NETWORK), now))
    }

    @Test
    fun `没有记录时走正常补签分支`() {
        val now = at(2026, 11, 27, 20, 30)
        assertFalse(late(null, now))
    }
}
