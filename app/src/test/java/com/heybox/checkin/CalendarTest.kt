package com.heybox.checkin

import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.ui.mondayIndex
import com.heybox.checkin.ui.monthGrids
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 10)
        }.timeInMillis

    private fun record(millis: Long) = RunRecord(
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

    @Test
    fun `周一排第一列`() {
        // 2026-11-23 是周一，2026-11-22 是周日
        assertEquals(0, mondayIndex(2026, 11, 23))
        assertEquals(6, mondayIndex(2026, 11, 22))
        assertEquals(6, mondayIndex(2026, 11, 1))
    }

    @Test
    fun `按自然月切开，新的月份在前`() {
        val grids = monthGrids(emptyList(), at(2026, 11, 25), days = 30)
        assertEquals(2, grids.size)
        assertEquals("11月", grids[0].label)
        assertEquals("10月", grids[1].label)
    }

    @Test
    fun `月初补空位对齐星期`() {
        val grids = monthGrids(emptyList(), at(2026, 11, 25), days = 30)
        val november = grids[0]
        // 11 月 1 日是周日，前面要空 6 格
        for (index in 0 until 6) assertNull(november.cells[index])
        assertEquals(1, november.cells[6]?.day)
        assertEquals(25, november.days.last().day)
        assertEquals(31, november.cells.size)
    }

    @Test
    fun `跨年时月份带上年份`() {
        val grids = monthGrids(emptyList(), at(2027, 1, 10), days = 30)
        assertEquals("1月", grids[0].label)
        assertEquals("2026年12月", grids[1].label)
    }

    @Test
    fun `格子带着当天的记录状态`() {
        val history = listOf(record(at(2026, 11, 24)))
        val grids = monthGrids(history, at(2026, 11, 25), days = 30)
        val cells = grids[0].days
        val day24 = cells.first { it.day == 24 }
        assertEquals(true, day24.executed)
        assertEquals(90L, day24.expDelta)
        assertTrue(cells.first { it.day == 23 }.state.name == "NONE")
    }
}
