package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuyerTest {
    private fun wall(x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(generateId("w"), P(x1, y1), P(x2, y2), 20.0)

    @Test
    fun singleRoomNetAndGross() {
        // 4 x 3 m to the wall centres, 20 cm walls.
        val p = PlanOps.withRooms(Project(walls = listOf(
            wall(0.0, 0.0, 400.0, 0.0), wall(400.0, 0.0, 400.0, 300.0),
            wall(400.0, 300.0, 0.0, 300.0), wall(0.0, 300.0, 0.0, 0.0),
        ), price = 3_000_000_000.0))
        val s = Buyer.summary(p)
        assertEquals(3.8 * 2.8, s.netM2, 0.01)
        // The outer edge would be 4.2 x 3.2; corners are left out (4 x 0.01 m²).
        assertEquals(4.2 * 3.2 - 0.04, s.grossM2, 0.01)
        assertEquals(3_000_000_000.0 / (3.8 * 2.8), s.pricePerNetM2!!, 1e4)
    }

    @Test
    fun twoRoomsShareTheInnerWall() {
        val p = PlanOps.withRooms(Project(walls = listOf(
            wall(0.0, 0.0, 600.0, 0.0), wall(600.0, 0.0, 600.0, 300.0),
            wall(600.0, 300.0, 0.0, 300.0), wall(0.0, 300.0, 0.0, 0.0),
            wall(300.0, 0.0, 300.0, 300.0),
        )))
        val s = Buyer.summary(p)
        assertEquals(2, s.rooms.size)
        assertEquals(2 * 2.8 * 2.8, s.netM2, 0.01)
        assertEquals(6.2 * 3.2 - 0.04, s.grossM2, 0.01)
        assertNull(s.pricePerNetM2)
    }
}
