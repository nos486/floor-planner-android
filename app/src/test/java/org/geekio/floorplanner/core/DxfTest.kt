package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DxfTest {
    private fun entities(dxf: String, kind: String, layer: String? = null): Int {
        val lines = dxf.lines()
        var n = 0
        var i = 0
        while (i + 3 < lines.size) {
            if (lines[i] == "0" && lines[i + 1] == kind && (layer == null || (lines[i + 2] == "8" && lines[i + 3] == layer))) n++
            i += 2
        }
        return n
    }

    @Test
    fun wallWithDoor() {
        val p = PlanOps.withRooms(
            Project(
                walls = listOf(Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0)),
                doors = listOf(Door("d", "w1", 200.0, 90.0)),
            )
        )
        val dxf = Dxf.export(p, showDimensions = false)
        assertTrue(dxf.startsWith("0\nSECTION\n2\nHEADER"))
        assertTrue(dxf.trimEnd().endsWith("0\nEOF"))
        // Every code has a value.
        assertEquals(0, dxf.trimEnd().lines().size % 2)
        // Two solid pieces, each with two faces and two caps.
        assertEquals(8, entities(dxf, "LINE", "WALLS"))
        assertEquals(1, entities(dxf, "ARC", "DOORS"))
        assertEquals(1, entities(dxf, "LINE", "DOORS"))
        // y is flipped: the door swings to +y on the plan, so to -y in the file.
        assertTrue(dxf.contains("\n20\n-90.00\n") || dxf.contains("\n21\n-90.00\n"))
    }
}
