package com.heybox.checkin

import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.csvField
import com.heybox.checkin.data.historyCsv
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.heybox.checkin.data.historyJson
import com.heybox.checkin.data.recordsFromJson
import org.junit.Assert.assertNull

class HistoryExportTest {

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
        coin = 1000,
        battery = 5,
        level = 3,
        maxExp = 200,
        signed = true,
        taskStates = mapOf("sign" to "finish", "1" to "finish"),
    )

    @Test
    fun `CSV 带 BOM 和表头`() {
        val csv = historyCsv(emptyList())
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("日期时间,状态,经验增量"))
    }

    @Test
    fun `按时间正序一天一行`() {
        val csv = historyCsv(
            listOf(
                done(at(2026, 11, 25)),
                done(at(2026, 11, 24)),
            ),
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("2026-11-24"))
        assertTrue(lines[2].startsWith("2026-11-25"))
    }

    @Test
    fun `三种状态写成人话`() {
        val skipped = done(at(2026, 11, 24)).copy(executed = false, signed = false)
        val failed = RunRecord.failure(at(2026, 11, 25), "连接超时", FailureKind.NETWORK)
        val csv = historyCsv(listOf(done(at(2026, 11, 23)), skipped, failed))
        val lines = csv.trim().lines()
        assertTrue(lines[1].contains(",完成,"))
        assertTrue(lines[2].contains(",其它入口,"))
        assertTrue(lines[3].contains(",失败,"))
        assertTrue(lines[3].contains("连接超时"))
    }

    @Test
    fun `逗号和引号按 RFC 4180 转义`() {
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"他说\"\"好\"\"\"", csvField("他说\"好\""))
        assertEquals("普通", csvField("普通"))
        // 失败原因里带逗号时整格加引号，列数不会错位
        val failed = RunRecord.failure(at(2026, 11, 25), "服务异常,稍后重试", FailureKind.SERVER)
        val line = historyCsv(listOf(failed)).trim().lines()[1]
        assertTrue(line.contains("\"服务异常,稍后重试\""))
    }

    @Test
    fun `没有增量的日子留空而不是写 0`() {
        val skipped = done(at(2026, 11, 24))
            .copy(executed = false, expDelta = null, coinDelta = 0, batteryDelta = null)
        val line = historyCsv(listOf(skipped)).trim().lines()[1]
        // 日期时间,状态,经验增量,H币增量,电池增量 → 增量为空的列相邻两个逗号
        assertTrue(line.contains(",其它入口,,0,,"))
    }

    @Test
    fun `JSON 备份往返保留全部字段`() {
        val record = done(at(2026, 11, 25))
        val restored = recordsFromJson(historyJson(listOf(record)))!!
        assertEquals(1, restored.size)
        assertEquals(record, restored[0])
    }

    @Test
    fun `失败记录的分类进 JSON`() {
        val failed = RunRecord.failure(at(2026, 11, 25), "连接超时", FailureKind.NETWORK)
        val restored = recordsFromJson(historyJson(listOf(failed)))!!
        assertEquals("连接超时", restored[0].error)
        assertEquals("network", restored[0].errorKind)
        assertEquals(false, restored[0].signed)
    }

    @Test
    fun `不是盒签备份或坏 JSON 返回 null`() {
        assertNull(recordsFromJson("不是JSON"))
        assertNull(recordsFromJson("""{"app":"other","records":[]}"""))
        assertNull(recordsFromJson("""{"app":"heybox-checkin"}"""))
    }
}
