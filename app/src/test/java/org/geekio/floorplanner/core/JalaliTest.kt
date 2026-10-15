package org.geekio.floorplanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class JalaliTest {
    @Test
    fun knownDates() {
        assertEquals(Jalali.Date(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(Jalali.Date(1405, 7, 23), Jalali.fromGregorian(2026, 10, 15))
        assertEquals(Jalali.Date(1402, 12, 29), Jalali.fromGregorian(2024, 3, 19))
        assertEquals(Jalali.Date(1403, 12, 30), Jalali.fromGregorian(2025, 3, 20))
    }

    @Test
    fun formatsInPersian() {
        assertEquals("۲۳ مهر ۱۴۰۵", Jalali.format(Jalali.Date(1405, 7, 23)))
    }
}
