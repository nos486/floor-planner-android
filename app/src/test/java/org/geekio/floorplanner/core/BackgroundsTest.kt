package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Background
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundsTest {
    @Test
    fun placeCentres() {
        val bg = Backgrounds.place(1000, 500, P(0.0, 0.0), 800.0)
        assertEquals(0.8, bg.scale, 1e-9)
        assertEquals(-400.0, bg.x, 1e-9)
        assertEquals(-200.0, bg.y, 1e-9)
    }

    @Test
    fun calibrateKeepsFirstPoint() {
        val bg = Background(x = 100.0, y = 50.0, scale = 2.0)
        val a = P(300.0, 250.0)
        // Image pixel under a before calibrating.
        val px = (a.x - bg.x) / bg.scale
        val py = (a.y - bg.y) / bg.scale
        // A 200-unit line that really is 500 long.
        val c = Backgrounds.calibrate(bg, a, P(500.0, 250.0), 500.0)
        assertEquals(5.0, c.scale, 1e-9)
        assertEquals(a.x, c.x + px * c.scale, 1e-9)
        assertEquals(a.y, c.y + py * c.scale, 1e-9)
    }
}
