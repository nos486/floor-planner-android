package org.geekio.floorplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class UnitsTest {
    private val us = Locale.US

    @Test
    fun formatsMetric() {
        assertEquals("3.25 m", Units.length(3.25, UnitSystem.METRIC, us))
        assertEquals("12.50 m²", Units.area(12.5, UnitSystem.METRIC, us))
        assertEquals("20 cm", Units.small(20.0, UnitSystem.METRIC, us))
    }

    @Test
    fun formatsImperial() {
        // 3.25 m = 127.95 in = 10' 8"
        assertEquals("10' 8\"", Units.length(3.25, UnitSystem.IMPERIAL, us))
        assertEquals("134.5 ft²", Units.area(12.5, UnitSystem.IMPERIAL, us))
        assertEquals("7.9\"", Units.small(20.0, UnitSystem.IMPERIAL, us))
        // Rounding up to a whole foot does not print 12 inches.
        assertEquals("1' 0\"", Units.length(0.3045, UnitSystem.IMPERIAL, us))
    }

    @Test
    fun parsesLengths() {
        assertEquals(3.25, Units.parseLength("3.25", UnitSystem.METRIC)!!, 1e-9)
        assertEquals(3.25, Units.parseLength("۳٫۲۵", UnitSystem.METRIC)!!, 1e-9)
        val tenEight = (10 * 12 + 8) * 0.0254
        assertEquals(tenEight, Units.parseLength("10' 8\"", UnitSystem.IMPERIAL)!!, 1e-9)
        assertEquals(tenEight, Units.parseLength("10'8", UnitSystem.IMPERIAL)!!, 1e-9)
        assertEquals(tenEight, Units.parseLength("10 8", UnitSystem.IMPERIAL)!!, 1e-9)
        assertEquals(10.5 * 12 * 0.0254, Units.parseLength("10.5", UnitSystem.IMPERIAL)!!, 1e-9)
        assertNull(Units.parseLength("abc", UnitSystem.IMPERIAL))
        assertNull(Units.parseLength("0", UnitSystem.METRIC))
    }

    @Test
    fun editValueRoundTrips() {
        for (u in UnitSystem.entries) {
            val m = Units.parseLength(Units.editValue(4.2, u), u)!!
            assertEquals(4.2, m, 0.013)
        }
    }
}
