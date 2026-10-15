package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class VersionsTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(id, P(x1, y1), P(x2, y2), 20.0)
    private val box = listOf(
        wall("w1", 0.0, 0.0, 400.0, 0.0), wall("w2", 400.0, 0.0, 400.0, 300.0),
        wall("w3", 400.0, 300.0, 0.0, 300.0), wall("w4", 0.0, 300.0, 0.0, 0.0),
    )

    @Test
    fun diffFindsAddedAndRemovedWalls() {
        val before = PlanOps.withRooms(Project(walls = box + wall("w5", 200.0, 0.0, 200.0, 300.0)))
        // The middle wall is taken out, one wall is redrawn backwards (still the same wall).
        val after = PlanOps.withRooms(Project(walls = box.dropLast(1) + wall("x", 0.0, 0.0, 0.0, 300.0)))
        val d = Compare.diff(before, after)
        assertEquals(listOf("w5"), d.removedWalls.map { it.id })
        assertTrue(d.addedWalls.isEmpty())
        assertEquals(12.0, d.areaBefore, 1e-9)
        assertEquals(12.0, d.areaAfter, 1e-9)
        assertTrue(d.changedRooms.isNotEmpty())
    }

    @Test
    fun storeKeepsVersions() {
        val dir = Files.createTempDirectory("fp").toFile()
        try {
            val store = ProjectStore(dir)
            val id = store.create(Project(walls = box))
            store.saveVersion(id, "Before", PlanOps.withRooms(Project(walls = box)), time = 1000)
            store.saveVersion(id, "After", Project(walls = box.take(2)), time = 2000)
            val list = store.versions(id)
            assertEquals(listOf("After", "Before"), list.map { it.name })
            assertEquals(4, store.loadVersion(id, list[1].key)!!.walls.size)
            // Versions don't show up as projects.
            assertEquals(1, store.list().size)
            store.deleteVersion(id, list[0].key)
            assertEquals(1, store.versions(id).size)
            store.delete(id)
            assertTrue(store.versions(id).isEmpty())
            assertNull(store.load(id))
        } finally {
            dir.deleteRecursively()
        }
    }
}
