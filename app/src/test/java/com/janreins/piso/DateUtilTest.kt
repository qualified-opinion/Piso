package com.janreins.piso

import com.janreins.piso.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class DateUtilTest {

    @Test
    fun shiftMonthKey_crossesYearBoundaries() {
        assertEquals("2026-01", DateUtil.shiftMonthKey("2025-12", 1))
        assertEquals("2025-12", DateUtil.shiftMonthKey("2026-01", -1))
        assertEquals("September 2026", DateUtil.getMonthDisplayName("2026-09"))
    }

    @Test
    fun dayHeader_saysTodayYesterdayOrTheDate() {
        val today = LocalDate.of(2026, 11, 9).toEpochDay()
        assertEquals("Today", DateUtil.formatDayHeader(today, today))
        assertEquals("Yesterday", DateUtil.formatDayHeader(today - 1, today))
        assertEquals("Tue, Nov 3", DateUtil.formatDayHeader(LocalDate.of(2026, 11, 3).toEpochDay(), today))
        // A day in another year shows the year too
        assertEquals("Wed, Dec 31, 2025", DateUtil.formatDayHeader(LocalDate.of(2025, 12, 31).toEpochDay(), today))
    }

    @Test
    fun pickerConversion_keepsCalendarDayAndTimeOfDay() {
        val zone = ZoneId.systemDefault()
        val original = LocalDateTime.of(2026, 3, 15, 21, 45).atZone(zone).toInstant().toEpochMilli()

        // Picker value for the same day is UTC midnight of March 15
        val pickerValue = DateUtil.toPickerUtcMillis(original)
        assertEquals(
            LocalDateTime.of(2026, 3, 15, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            pickerValue
        )

        // Choosing March 2 keeps 21:45 local time
        val picked = LocalDateTime.of(2026, 3, 2, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val result = DateUtil.fromPickerUtcMillis(picked, keepTimeOf = original)
        assertEquals(LocalDateTime.of(2026, 3, 2, 21, 45), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(result), zone))
    }
}
