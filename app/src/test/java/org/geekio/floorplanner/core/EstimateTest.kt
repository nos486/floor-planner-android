package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Costs
import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Test

class EstimateTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(id, P(x1, y1), P(x2, y2), 20.0)

    // Two rooms (3x3 and 1x3 m) side by side, a door between them and a window in the big one.
    private val plan = PlanOps.withRooms(
        Project(
            walls = listOf(
                wall("w1", 0.0, 0.0, 400.0, 0.0), wall("w2", 400.0, 0.0, 400.0, 300.0),
                wall("w3", 400.0, 300.0, 0.0, 300.0), wall("w4", 0.0, 300.0, 0.0, 0.0),
                wall("w5", 300.0, 0.0, 300.0, 300.0),
            ),
            doors = listOf(Door("d1", "w5", 150.0, 90.0)),
            windows = listOf(PlanWindow("win1", "w4", 150.0, 100.0)),
            wallHeight = 250.0,
            costs = Costs(floorPerM2 = 10.0, paintPerLiter = 2.0, skirtingPerM = 1.0, wastePercent = 10.0),
        )
    )

    @Test
    fun quantities() {
        val e = Estimates.estimate(plan)
        assertEquals(2, e.rooms.size)
        val big = e.rooms.first { it.floorM2 > 5 }
        val small = e.rooms.first { it.floorM2 < 5 }
        assertEquals(12.0, big.perimeterM, 1e-6)
        // 12 m × 2.5 m minus the door (0.9 × 2.1) and the window (1.0 × 1.2).
        assertEquals(30.0 - 1.89 - 1.2, big.wallM2, 1e-6)
        assertEquals(8.0 * 2.5 - 1.89, small.wallM2, 1e-6)
        assertEquals(12.0 - 0.9, big.skirtingM, 1e-6)
        assertEquals(12.0 * 1.1, e.coveringM2, 1e-6)
        assertEquals(e.wallM2 * 2 / 10, e.paintLiters, 1e-6)
    }

    @Test
    fun costs() {
        val e = Estimates.estimate(plan)
        val c = Estimates.cost(e, plan)
        assertEquals(e.coveringM2 * 10, c.floor, 1e-6)
        assertEquals(c.floor + e.paintLiters * 2 + e.skirtingM, c.total, 1e-6)
    }
}
