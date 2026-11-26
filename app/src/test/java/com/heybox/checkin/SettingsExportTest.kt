package com.heybox.checkin

import com.heybox.checkin.data.SettingsExport
import com.heybox.checkin.data.SharedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsExportTest {

    @Test
    fun `导出再导入保持一致`() {
        val config = SharedSettings(
            scheduled = true,
            startHour = 9,
            endHour = 21,
            shares = setOf("1", "31"),
            hideFromRecents = true,
            appearance = "dark",
        )
        assertEquals(config, SettingsExport.decode(SettingsExport.encode(config)))
    }

    @Test
    fun `认不出的文本返回空`() {
        assertNull(SettingsExport.decode("随便一段文字"))
        assertNull(SettingsExport.decode(""))
    }

    @Test
    fun `时段越界会夹回合理范围`() {
        val config = SettingsExport.decode("盒签配置 v1\nwindow=25:24\ndaily=on")!!
        assertTrue(config.startHour in 0..22)
        assertTrue(config.endHour in (config.startHour + 1)..23)
    }

    @Test
    fun `未知分享项和外观会被忽略`() {
        val config = SettingsExport.decode("盒签配置 v1\nshares=1,99,abc\nappearance=rainbow")!!
        assertEquals(setOf("1"), config.shares)
        assertEquals("auto", config.appearance)
    }

    @Test
    fun `缺省行按默认值处理`() {
        val config = SettingsExport.decode("盒签配置 v1")!!
        assertTrue(!config.scheduled)
        assertEquals(8, config.startHour)
        assertEquals(20, config.endHour)
        assertEquals("auto", config.appearance)
    }
}
