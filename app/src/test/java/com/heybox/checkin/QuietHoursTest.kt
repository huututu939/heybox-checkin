package com.heybox.checkin

import com.heybox.checkin.data.SessionStore
import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 夜间静音窗口：22 点到次日 8 点，只压成功通知。 */
class QuietHoursTest {

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, 10, 20, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `静音窗口是二十二点到次日八点`() {
        assertTrue(SessionStore.inQuietHours(at(22)))
        assertTrue(SessionStore.inQuietHours(at(23, 30)))
        assertTrue(SessionStore.inQuietHours(at(0)))
        assertTrue(SessionStore.inQuietHours(at(7, 59)))
        assertFalse(SessionStore.inQuietHours(at(8)))
        assertFalse(SessionStore.inQuietHours(at(12)))
        assertFalse(SessionStore.inQuietHours(at(21, 59)))
    }
}
