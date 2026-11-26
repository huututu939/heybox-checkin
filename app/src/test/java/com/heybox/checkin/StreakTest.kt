package com.heybox.checkin

import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.lastSignedAt
import com.heybox.checkin.data.missStreak
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 10)
        }.timeInMillis

    private fun done(millis: Long) = RunRecord(
        at = millis,
        executed = true,
        expDelta = 90,
        coinDelta = 300,
        batteryDelta = 1,
        exp = 170,
        coin = 766,
        battery = 5,
        level = 3,
        maxExp = 200,
        signed = true,
        taskStates = mapOf("sign" to "finish"),
    )

    private fun failed(millis: Long) =
        RunRecord.failure(millis, "连接超时", FailureKind.NETWORK).withBalanceOf(done(millis))

    /** 「其它入口已完成」：没有执行但有签到状态，应当算完成而不是漏签。 */
    private fun skipped(millis: Long) =
        done(millis).copy(executed = false, expDelta = null, coinDelta = 0, batteryDelta = null)

    @Test
    fun `今天和昨天都失败算连续两天`() {
        val now = at(2026, 11, 25, 14)
        val history = listOf(failed(at(2026, 11, 25)), failed(at(2026, 11, 24)))
        assertEquals(2, missStreak(history, now))
    }

    @Test
    fun `今天还没到点不算漏签`() {
        val now = at(2026, 11, 25, 7)
        val history = listOf(failed(at(2026, 11, 24)), failed(at(2026, 11, 23)))
        // 今天没有记录，从昨天开始数：24、23 两天
        assertEquals(2, missStreak(history, now))
    }

    @Test
    fun `今天签上了就是零`() {
        val now = at(2026, 11, 25, 14)
        val history = listOf(done(at(2026, 11, 25)), failed(at(2026, 11, 24)))
        assertEquals(0, missStreak(history, now))
    }

    @Test
    fun `昨天已由别的入口完成也从零开始`() {
        val now = at(2026, 11, 25, 7)
        val history = listOf(skipped(at(2026, 11, 24)), failed(at(2026, 11, 23)))
        assertEquals(0, missStreak(history, now))
    }

    @Test
    fun `连续多天没有记录也算漏签`() {
        val now = at(2026, 11, 25, 7)
        // 20 号签上了，之后一直没记录：21/22/23/24 四天没签
        val history = listOf(done(at(2026, 11, 20)))
        assertEquals(4, missStreak(history, now))
    }

    @Test
    fun `不会数到第一条记录之前`() {
        val now = at(2026, 11, 25, 7)
        val history = listOf(failed(at(2026, 11, 24)))
        assertEquals(1, missStreak(history, now))
    }

    @Test
    fun `没有历史记录时不报警`() {
        assertEquals(0, missStreak(emptyList(), at(2026, 11, 25, 7)))
        assertNull(lastSignedAt(emptyList()))
    }

    @Test
    fun `最近成功时间取最后一条完成的记录`() {
        val ok = at(2026, 11, 22)
        val history = listOf(failed(at(2026, 11, 24)), done(ok), skipped(at(2026, 11, 21)))
        assertEquals(ok, lastSignedAt(history))
    }
}
