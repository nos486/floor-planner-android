package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(id, P(x1, y1), P(x2, y2), 20.0)
    private val box = listOf(
        wall("w1", 0.0, 0.0, 400.0, 0.0), wall("w2", 400.0, 0.0, 400.0, 300.0),
        wall("w3", 400.0, 300.0, 0.0, 300.0), wall("w4", 0.0, 300.0, 0.0, 0.0),
    )

    @Test
    fun noonSunIsSouthAndAsHighAsExpected() {
        val summer = Sun.position(35.7, 172, 12.0)
        assertEquals(180.0, summer.azimuth, 1.0)
        assertEquals(90 - 35.7 + 23.44, summer.elevation, 0.6)
        val winter = Sun.position(35.7, 355, 12.0)
        assertEquals(90 - 35.7 - 23.44, winter.elevation, 0.6)
        // Morning sun is in the east, evening sun in the west.
        assertTrue(Sun.position(35.7, 172, 8.0).azimuth in 60.0..120.0)
        assertTrue(Sun.position(35.7, 172, 16.0).azimuth in 240.0..300.0)
        assertTrue(!Sun.position(35.7, 172, 0.0).isUp)
    }

    @Test
    fun southWindowLightsTheFloorNorthWindowDoesNot() {
        // North is up: w1 (y = 0) faces north, w3 (y = 300) faces south.
        val south = PlanOps.withRooms(Project(walls = box, windows = listOf(PlanWindow("s", "w3", 200.0, 120.0))))
        val noon = Sun.position(35.7, 355, 12.0)
        val patch = Sun.patches(south, noon).single()
        // Light falls into the room, going north (smaller y), further for the window top than for the sill.
        assertTrue(patch.polygon.all { it.y < 300 })
        assertTrue(patch.polygon[3].y < patch.polygon[0].y)

        val north = PlanOps.withRooms(Project(walls = box, windows = listOf(PlanWindow("n", "w1", 200.0, 120.0))))
        assertTrue(Sun.patches(north, noon).isEmpty())
        assertEquals(0.0, Sun.hours(north, 355).single().hours, 0.01)
        assertTrue(Sun.hours(south, 355).single().hours > 6)

        // Turning the plan so north points down swaps them.
        assertTrue(Sun.patches(north.copy(north = 180.0), noon).isNotEmpty())
    }
}
