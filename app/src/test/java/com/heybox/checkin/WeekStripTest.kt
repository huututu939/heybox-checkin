package com.heybox.checkin

import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.ui.DayState
import com.heybox.checkin.ui.weekCells
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekStripTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 10)
        }.timeInMillis

    private fun record(atMillis: Long, signed: Boolean = true, executed: Boolean = true) = RunRecord(
        at = atMillis,
        executed = executed,
        expDelta = if (executed) 90 else null,
        coinDelta = if (executed) 300 else 0,
        batteryDelta = if (executed) 1 else null,
        exp = 170,
        coin = 766,
        battery = 5,
        level = 3,
        maxExp = 200,
        signed = signed,
        taskStates = mapOf("sign" to if (signed) "finish" else "waiting"),
    )

    @Test
    fun `七天按时间正序，今天在最后一格`() {
        val now = at(2026, 11, 25, 14)
        val cells = weekCells(emptyList(), now)
        assertEquals(7, cells.size)
        assertFalse(cells.first().today)
        assertTrue(cells.last().today)
        assertEquals("11/19", cells.first().monthDay)
        assertEquals("11/25", cells.last().monthDay)
    }

    @Test
    fun `有签到记录的那天标记为完成`() {
        val now = at(2026, 11, 25, 14)
        val cells = weekCells(listOf(record(at(2026, 11, 23, 8))), now)
        assertEquals(DayState.DONE, cells[4].state)
        assertEquals(DayState.NONE, cells[3].state)
    }

    @Test
    fun `已由其它入口完成的那天标记为跳过`() {
        val now = at(2026, 11, 25, 14)
        val cells = weekCells(listOf(record(at(2026, 11, 24, 9), signed = true, executed = false)), now)
        assertEquals(DayState.SKIPPED, cells[5].state)
        assertFalse(cells[5].executed)
    }

    @Test
    fun `执行过但没签到成功标记为失败`() {
        val now = at(2026, 11, 25, 14)
        val cells = weekCells(listOf(record(at(2026, 11, 22, 9), signed = false)), now)
        assertEquals(DayState.FAILED, cells[3].state)
    }

    @Test
    fun `星期标签去掉周字`() {
        val now = at(2026, 11, 25, 14)
        val cells = weekCells(emptyList(), now)
        assertTrue(cells.all { it.weekday.length == 1 })
        assertEquals("三", cells.last().weekday)
    }
}
