package com.heybox.checkin

import com.heybox.checkin.work.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/** 定时必须落在随机窗口内，且每天不是同一分钟。 */
class SchedulerTest {

    private val startHour = 8
    private val endHour = 20

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun triggerFallsInsideWindow() {
        val now = at(2026, 11, 24, 9, 15)
        repeat(200) { i ->
            val trigger = Scheduler.nextTriggerMillis(
                now = now,
                startHour = startHour,
                endHour = endHour,
                random = Random(i * 977 + 13),
            )
            assertTrue("必须晚于当前时间", trigger > now)
            assertTrue("不能超过一天之后", trigger - now <= 24L * 3600 * 1000)
            val hour = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.HOUR_OF_DAY)
            assertTrue("小时必须落在 $startHour–$endHour 之间，实际 $hour", hour in startHour until endHour)
        }
    }

    @Test
    fun triggerVariesAcrossDraws() {
        val now = at(2026, 11, 24, 8, 0)
        val minutes = (0 until 100).map { i ->
            val trigger = Scheduler.nextTriggerMillis(now, startHour, endHour, Random(i * 31 + 7))
            val calendar = Calendar.getInstance().apply { timeInMillis = trigger }
            calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        }
        assertTrue("不能所有抽签都落在同一分钟", minutes.toSet().size > 20)
        // 这组窗口总共有 720 分钟，至少覆盖到较宽的范围
        assertTrue("分布过窄：${minutes.min()}–${minutes.min()}..${minutes.max()}", minutes.max() - minutes.min() > 300)
    }

    @Test
    fun windowIsClampedToValidRange() {
        val now = at(2026, 11, 24, 0, 5)
        val trigger = Scheduler.nextTriggerMillis(now, startHour = 23, endHour = 24, random = Random(5))
        val hour = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.HOUR_OF_DAY)
        assertEquals(23, hour)
    }

    @Test
    fun lateAfternoonPickStaysToday() {
        // 18:00 才排今天，随机点不能因为落在 08:00–18:00 就被推到明天，否则会静默漏签。
        val now = at(2026, 11, 24, 18, 0)
        repeat(100) { i ->
            val trigger = Scheduler.nextTriggerMillis(now, startHour, endHour, Random(i * 131 + 11))
            assertTrue("必须还在今天", Scheduler.isSameLocalDay(trigger, now))
            assertTrue("必须在 20:00 前", trigger <= at(2026, 11, 24, 20, 0))
        }
    }

    @Test
    fun tomorrowPickUsedWhenWindowPassed() {
        val now = at(2026, 11, 24, 21, 30)
        val trigger = Scheduler.nextTriggerMillis(now, startHour, endHour, Random(3))
        assertTrue(!Scheduler.isSameLocalDay(trigger, now))
        val calendar = Calendar.getInstance().apply { timeInMillis = trigger }
        assertTrue(calendar.get(Calendar.HOUR_OF_DAY) in startHour until endHour)
    }
}
