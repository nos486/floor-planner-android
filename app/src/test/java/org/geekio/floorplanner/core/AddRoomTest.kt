package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddRoomTest {
    @Test
    fun rectangleMakesOneClosedRoom() {
        // Corners given in any order.
        val p = PlanOps.addRoom(Project(), P(400.0, 300.0), P(0.0, 0.0), 20.0)!!
        assertEquals(4, p.walls.size)
        assertEquals(12.0, p.rooms.single().areaM2, 0.01)
    }

    @Test
    fun roomNextToARoomSharesItsWall() {
        val one = PlanOps.addRoom(Project(), P(0.0, 0.0), P(400.0, 300.0), 20.0)!!
        val two = PlanOps.addRoom(one, P(400.0, 0.0), P(700.0, 300.0), 20.0)!!
        assertEquals(2, two.rooms.size)
        // The shared side isn't drawn twice.
        assertEquals(7, two.walls.size)
    }

    @Test
    fun tooThinIsRefused() {
        assertNull(PlanOps.addRoom(Project(), P(0.0, 0.0), P(400.0, 2.0), 20.0))
    }
}
