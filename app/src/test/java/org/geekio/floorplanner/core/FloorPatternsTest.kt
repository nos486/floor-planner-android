package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPatternsTest {
    private val box = listOf(
        Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0),
        Wall.create("w2", P(400.0, 0.0), P(400.0, 300.0), 20.0),
        Wall.create("w3", P(400.0, 300.0), P(0.0, 300.0), 20.0),
        Wall.create("w4", P(0.0, 300.0), P(0.0, 0.0), 20.0),
    )

    @Test
    fun everyMaterialHasLines() {
        for (m in FloorPatterns.materials) {
            assertTrue(m, FloorPatterns.lines(m, P(0.0, 0.0), P(300.0, 200.0)).isNotEmpty())
            assertTrue(m, FloorPatterns.spacing(m) > 0)
        }
        assertTrue(FloorPatterns.lines(null, P(0.0, 0.0), P(300.0, 200.0)).isEmpty())
    }

    @Test
    fun tileGridCoversTheBox() {
        val lines = FloorPatterns.lines("tile", P(0.0, 0.0), P(240.0, 120.0))
        // y = 0, 60, 120 and x = 0, 60, 120, 180, 240.
        assertEquals(3 + 5, lines.size)
    }

    @Test
    fun parseColor() {
        assertEquals(0xFFE8D5B7, FloorPatterns.parseColor("#e8d5b7"))
        assertNull(FloorPatterns.parseColor("red"))
        assertNull(FloorPatterns.parseColor(null))
    }

    @Test
    fun colorAndFloorSurviveWallEditsAndJson() {
        var p = PlanOps.withRooms(Project(walls = box))
        val id = p.rooms.single().id
        p = PlanOps.setRoomFloor(PlanOps.setRoomColor(p, id, "#c8a27a"), id, "wood")
        // Moving a corner re-detects the room; its style must stay.
        p = PlanOps.moveVertex(p, P(400.0, 300.0), P(420.0, 300.0))
        val room = ProjectIO.fromJson(ProjectIO.toJson(p))!!.rooms.single()
        assertEquals("#c8a27a", room.color)
        assertEquals("wood", room.floor)
        assertTrue(ProjectIO.toSvg(p).contains("clip-path"))
    }
}
