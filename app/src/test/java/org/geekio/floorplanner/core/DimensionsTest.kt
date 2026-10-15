package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionsTest {
    private val box = listOf(
        Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0),
        Wall.create("w2", P(400.0, 0.0), P(400.0, 300.0), 20.0),
        Wall.create("w3", P(400.0, 300.0), P(0.0, 300.0), 20.0),
        Wall.create("w4", P(0.0, 300.0), P(0.0, 0.0), 20.0),
    )

    @Test
    fun overallMeasuresOuterFacesOutsideThePlan() {
        val (top, left) = Dimensions.overall(Project(walls = box), 40.0)
        assertEquals(420.0, top.start.dist(top.end), 1e-9)
        assertEquals(-50.0, top.start.y, 1e-9) // outer face at -10, then 40 further out
        assertEquals(320.0, left.start.dist(left.end), 1e-9)
        assertEquals(-50.0, left.start.x, 1e-9)
        assertTrue(top.labelAt(10.0).y < top.start.y)
    }

    @Test
    fun noOverallWithoutWalls() {
        assertTrue(Dimensions.overall(Project(), 40.0).isEmpty())
    }

    @Test
    fun addHitMoveAndDelete() {
        val (p, d) = PlanOps.addDimension(Project(), P(0.0, 0.0), P(200.0, 0.0))!!
        assertEquals(200.0, d.length, 1e-9)
        val moved = PlanOps.setDimensionOffset(p, d.id, Dimensions.offsetThrough(d, P(50.0, 30.0)))
        assertEquals(30.0, moved.dimension(d.id)!!.offset, 1e-9)
        assertNull(Dimensions.hit(moved, P(100.0, 0.0), 5.0))
        assertEquals(Selection(Kind.DIMENSION, d.id), PlanOps.hit(moved, P(100.0, 31.0), 5.0))
        val loaded = ProjectIO.fromJson(ProjectIO.toJson(moved))!!
        assertEquals(moved.dimensions, loaded.dimensions)
        assertTrue(PlanOps.delete(loaded, Selection(Kind.DIMENSION, d.id)).dimensions.isEmpty())
    }

    @Test
    fun tooShortMeasurementIsIgnored() {
        assertNull(PlanOps.addDimension(Project(), P(0.0, 0.0), P(1.0, 0.0)))
    }
}
