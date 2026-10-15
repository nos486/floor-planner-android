package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FurnitureTest {
    private val bed = Furniture.type("bed_double")!!

    @Test
    fun everyCatalogTypeHasShapes() {
        for (type in Furniture.catalog) {
            assertTrue(type.key, Furniture.shapes(Furniture.create(type, P.ZERO)).isNotEmpty())
        }
    }

    @Test
    fun localAndWorldRoundTrip() {
        val item = Furniture.create(bed, P(100.0, 50.0), rotation = 37.0)
        val local = P(12.0, -40.0)
        val back = Furniture.toLocal(item, Furniture.toWorld(item, local))
        assertEquals(local.x, back.x, 1e-9)
        assertEquals(local.y, back.y, 1e-9)
    }

    @Test
    fun containsRespectsRotation() {
        // 160 wide x 200 deep, turned 90 degrees: now 200 along x and 160 along y.
        val item = Furniture.create(bed, P.ZERO, rotation = 90.0)
        assertTrue(Furniture.contains(item, P(95.0, 0.0)))
        assertFalse(Furniture.contains(item, P(0.0, 95.0)))
    }

    @Test
    fun snapsBackAgainstNearbyWall() {
        // Horizontal wall along y = 0, item below it (y grows downwards).
        val wall = Wall.create("w", P(0.0, 0.0), P(400.0, 0.0), 20.0)
        val item = Furniture.create(bed, P(200.0, 115.0))
        val snapped = Furniture.snapToWall(item, P(200.0, 115.0), listOf(wall), 20.0)
        assertNotNull(snapped)
        // Back against the wall face (y = 10), so the centre is half the depth further.
        assertEquals(110.0, snapped!!.y, 0.01)
        assertEquals(0.0, snapped.rotation, 0.01)
        // The back edge is on the wall side.
        assertEquals(10.0, Furniture.toWorld(snapped, P(0.0, -snapped.depth / 2)).y, 0.01)
    }

    @Test
    fun snapTurnsItemToFaceAwayFromWall() {
        // Vertical wall along x = 0, item to its right.
        val wall = Wall.create("w", P(0.0, 0.0), P(0.0, 400.0), 20.0)
        val item = Furniture.create(bed, P(115.0, 200.0))
        val snapped = Furniture.snapToWall(item, P(115.0, 200.0), listOf(wall), 30.0)!!
        val back = Furniture.toWorld(snapped, P(0.0, -snapped.depth / 2))
        assertEquals(10.0, back.x, 0.01)
    }

    @Test
    fun noSnapWhenFarFromWalls() {
        val wall = Wall.create("w", P(0.0, 0.0), P(400.0, 0.0), 20.0)
        val item = Furniture.create(bed, P(200.0, 400.0))
        assertNull(Furniture.snapToWall(item, P(200.0, 400.0), listOf(wall), 20.0))
    }

    @Test
    fun itemsSurviveJsonAndAreHitFirst() {
        val wall = Wall.create("w", P(0.0, 0.0), P(400.0, 0.0), 20.0)
        val item = Furniture.create(bed, P(200.0, 110.0))
        val p = PlanOps.addItem(Project(walls = listOf(wall)), item)
        val loaded = ProjectIO.fromJson(ProjectIO.toJson(p))!!
        assertEquals(listOf(item), loaded.items)
        assertEquals(Selection(Kind.ITEM, item.id), PlanOps.hit(loaded, P(200.0, 120.0), 5.0))
        assertTrue(ProjectIO.toSvg(loaded).contains("rotate("))
    }

    @Test
    fun duplicateAndDelete() {
        val item = Furniture.create(bed, P.ZERO)
        val (p, copy) = PlanOps.duplicateItem(PlanOps.addItem(Project(), item), item.id)!!
        assertEquals(2, p.items.size)
        assertEquals(item.width + 20, copy.x - item.x, 0.01)
        assertEquals(1, PlanOps.delete(p, Selection(Kind.ITEM, copy.id)).items.size)
    }

    @Test
    fun rotationIsNormalized() {
        val item = Furniture.create(bed, P.ZERO)
        val p = PlanOps.rotateItem(PlanOps.addItem(Project(), item), item.id, -90.0)
        assertEquals(270.0, p.items.single().rotation, 0.0)
    }
}
