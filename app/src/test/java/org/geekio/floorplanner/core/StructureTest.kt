package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructureTest {
    private val wall = Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0)

    @Test
    fun doubleDoorHasTwoHalfLeaves() {
        val door = Door("d", "w1", 200.0, 160.0, style = Door.DOUBLE)
        val leaves = PlanOps.doorLeaves(PlanOps.openingGeometry(wall, door)!!, door)
        assertEquals(2, leaves.size)
        leaves.forEach { assertEquals(80.0, it.radius, 1e-9) }
        assertEquals(P(120.0, 0.0), leaves[0].hinge)
        assertEquals(P(280.0, 0.0), leaves[1].hinge)
        assertEquals(P(120.0, 80.0), leaves[0].leafEnd)
    }

    @Test
    fun slidingDoorHasPanelsAndNoSwing() {
        val door = Door("d", "w1", 200.0, 100.0, style = Door.SLIDING)
        val g = PlanOps.openingGeometry(wall, door)!!
        assertTrue(PlanOps.doorLeaves(g, door).isEmpty())
        val panels = PlanOps.slidingPanels(g)
        assertEquals(2, panels.size)
        // The panels overlap in the middle.
        assertTrue(panels[0].second.x > panels[1].first.x)
    }

    @Test
    fun stairsRiseToTheNextFloor() {
        val stairs = Item("s", "stairs", 100.0, 200.0, 100.0, 280.0)
        val faces = Scene3D.build(Project(items = listOf(stairs), wallHeight = 300.0))
        assertEquals(300.0, faces.flatMap { it.points }.maxOf { it.z }, 1e-9)
        assertEquals(10, faces.count { it.kind == FaceKind.ITEM_TOP })
    }

    @Test
    fun lowWallIsLowInThreeD() {
        val faces = Scene3D.build(Project(walls = listOf(wall.copy(height = 100.0)), wallHeight = 270.0))
        assertEquals(100.0, faces.flatMap { it.points }.maxOf { it.z }, 1e-9)
    }

    @Test
    fun columnsReachTheCeiling() {
        val faces = Scene3D.build(Project(items = listOf(Item("c", "column_round", 0.0, 0.0, 30.0, 30.0)), wallHeight = 280.0))
        assertEquals(280.0, faces.flatMap { it.points }.maxOf { it.z }, 1e-9)
    }
}
