package com.heybox.checkin

import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.ui.todayOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 跨天回归：首页曾经把昨天的记录当成今天渲染，
 * 于是早上打开 App 会看到「今天已由其它入口完成」，而实际今天还没跑。
 */
class DayRolloverTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun record(atMillis: Long) = RunRecord(
        at = atMillis,
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

    @Test
    fun `昨天的记录不算今天`() {
        val now = at(2026, 11, 25, 9, 10)
        assertNull(record(at(2026, 11, 24, 13, 41)).todayOrNull(now))
    }

    @Test
    fun `今天的记录算今天`() {
        val now = at(2026, 11, 25, 9, 10)
        assertNotNull(record(at(2026, 11, 25, 8, 5)).todayOrNull(now))
    }

    @Test
    fun `跨年边界按本地日切`() {
        val now = at(2027, 1, 1, 0, 5)
        assertNull(record(at(2026, 12, 31, 23, 50)).todayOrNull(now))
    }

    @Test
    fun `没有记录时返回 null`() {
        val none: RunRecord? = null
        assertNull(none.todayOrNull(at(2026, 11, 25, 9, 0)))
    }
}