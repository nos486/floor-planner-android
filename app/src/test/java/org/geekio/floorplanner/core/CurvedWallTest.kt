package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvedWallTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(id, P(x1, y1), P(x2, y2), 20.0)

    @Test
    fun semicirclePath() {
        // Bulge of half the chord: a half circle of radius 200 around (200, 0).
        val w = wall("w", 0.0, 0.0, 400.0, 0.0).copy(bulge = 200.0)
        val path = Walls.path(w)
        assertEquals(P(0.0, 0.0), path.first())
        assertEquals(P(400.0, 0.0), path.last())
        path.forEach { assertEquals(200.0, it.dist(P(200.0, 0.0)), 1e-6) }
        assertEquals(Math.PI * 200, Walls.length(w), 2.0)
        // perp of (1, 0) is (0, 1): the arc bulges to +y.
        assertTrue(path.all { it.y >= -1e-9 })
        assertEquals(P(200.0, 200.0), Walls.apex(w))
    }

    @Test
    fun shallowArcThroughApex() {
        val w = wall("w", 0.0, 0.0, 400.0, 0.0).copy(bulge = -50.0)
        val path = Walls.path(w)
        assertEquals(-50.0, path.minOf { it.y }, 0.5)
        assertEquals(-50.0, Walls.bulgeThrough(w.copy(bulge = 0.0), P(200.0, -50.0)), 1e-9)
        assertEquals(50.0, Walls.distance(w, P(200.0, 0.0)), 0.5)
    }

    @Test
    fun roomFollowsTheCurve() {
        val walls = listOf(
            wall("w1", 0.0, 0.0, 400.0, 0.0),
            wall("w2", 400.0, 0.0, 400.0, 300.0),
            // Bottom wall bows outwards (down) by 100.
            wall("w3", 400.0, 300.0, 0.0, 300.0).copy(bulge = -100.0),
            wall("w4", 0.0, 300.0, 0.0, 0.0),
        )
        val room = RoomDetector.detect(walls, org.geekio.floorplanner.model.Scale()).single()
        // Circular segment with chord 400 and height 100: r = 250, area ≈ 2.8 m².
        assertEquals(12.0 + 2.796, room.areaM2, 0.05)
    }

    @Test
    fun bendingDropsOpeningsAndClamps() {
        val p = Project(walls = listOf(wall("w", 0.0, 0.0, 400.0, 0.0)), doors = listOf(Door("d", "w", 200.0)))
        val bent = PlanOps.setWallBulge(p, "w", 900.0)
        assertEquals(200.0, bent.walls.single().bulge, 1e-9)
        assertTrue(bent.doors.isEmpty())
        assertEquals(0.0, PlanOps.setWallBulge(bent, "w", 1.0).walls.single().bulge, 1e-9)
    }

    @Test
    fun splitCurvedWallKeepsTheCircle() {
        val p = Project(walls = listOf(wall("w", 0.0, 0.0, 400.0, 0.0).copy(bulge = 200.0)))
        val (split, _) = PlanOps.splitWall(p, "w")!!
        assertEquals(2, split.walls.size)
        for (half in split.walls) {
            Walls.path(half).forEach { assertEquals(200.0, it.dist(P(200.0, 0.0)), 0.5) }
        }
    }

    @Test
    fun mirrorFlipsTheCurve() {
        val p = Project(walls = listOf(wall("w", 0.0, 0.0, 400.0, 0.0).copy(bulge = 80.0)))
        val m = GroupOps.mirror(p, setOf(Selection(Kind.WALL, "w")))
        val w = m.walls.single()
        // Mirroring left-right reverses a→b, so the apex has to stay on the same side (+y).
        assertEquals(80.0, Walls.apex(w).y, 1e-6)
    }
}
