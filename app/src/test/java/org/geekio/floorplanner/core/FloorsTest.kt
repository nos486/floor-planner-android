package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorsTest {
    private val box = listOf(
        Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0),
        Wall.create("w2", P(400.0, 0.0), P(400.0, 300.0), 20.0),
        Wall.create("w3", P(400.0, 300.0), P(0.0, 300.0), 20.0),
        Wall.create("w4", P(0.0, 300.0), P(0.0, 0.0), 20.0),
    )
    private val ground = PlanOps.withRooms(Project(walls = box))

    @Test
    fun singleFloorHasNoList() {
        assertEquals(1, Floors.count(ground))
        assertEquals(4, Floors.all(ground, "Ground").single().walls.size)
        assertNull(Floors.below(ground))
    }

    @Test
    fun addOpensEmptyFloorAndKeepsTheOneBelow() {
        val p = Floors.add(ground, "First", "Ground")
        assertEquals(2, p.floors.size)
        assertEquals(1, p.floorIndex)
        assertTrue(p.walls.isEmpty())
        assertEquals("Ground", p.floors[0].name)
        assertEquals(4, Floors.below(p)!!.walls.size)
        // The open floor's slot is only a placeholder.
        assertTrue(p.floors[1].walls.isEmpty())
    }

    @Test
    fun switchingSwapsContent() {
        var p = Floors.add(ground, "First", "Ground")
        p = p.copy(walls = box.take(2))
        p = Floors.switchTo(p, 0)
        assertEquals(4, p.walls.size)
        assertEquals(2, p.floors[1].walls.size)
        assertTrue(p.floors[0].walls.isEmpty())
        assertEquals(12.0 + 0.0, Floors.totalAreaM2(p), 0.001)
        p = Floors.switchTo(p, 1)
        assertEquals(2, p.walls.size)
    }

    @Test
    fun deleteOpensFloorBelow() {
        var p = Floors.add(ground, "First", "Ground")
        p = Floors.add(p, "Second", "")
        assertEquals(3, Floors.count(p))
        p = Floors.delete(p, 2)
        assertEquals(2, p.floors.size)
        assertEquals(1, p.floorIndex)
        p = Floors.delete(p, 0)
        assertEquals(0, p.floorIndex)
        assertEquals("First", p.floors.single().name)
        assertTrue(p.walls.isEmpty())
        // The last floor can't be deleted.
        assertEquals(p, Floors.delete(p, 0))
    }

    @Test
    fun floorsSurviveJson() {
        val p = Floors.rename(Floors.add(ground, "First", "Ground"), 1, "Roof")
        val back = ProjectIO.fromJson(ProjectIO.toJson(p))!!
        assertEquals(listOf("Ground", "Roof"), back.floors.map { it.name })
        assertEquals(4, Floors.switchTo(back, 0).walls.size)
    }
}
