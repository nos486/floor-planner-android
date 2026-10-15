package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Scene3DTest {
    private val wall = Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0)

    private fun maxZ(faces: List<Face>) = faces.flatMap { it.points }.maxOf { it.z }

    @Test
    fun plainWallIsOneBox() {
        val faces = Scene3D.build(Project(walls = listOf(wall), wallHeight = 250.0))
        // Two sides, top and two ends.
        assertEquals(5, faces.size)
        assertEquals(250.0, maxZ(faces), 1e-9)
    }

    @Test
    fun doorLeavesALintel() {
        val faces = Scene3D.build(Project(walls = listOf(wall), doors = listOf(Door("d", "w1", 200.0, 90.0)), wallHeight = 250.0))
        // Left piece, lintel, right piece.
        assertEquals(15, faces.size)
        val lintelBottoms = faces.filter { f -> f.points.all { it.x in 154.9..245.1 } && f.points.map { it.x }.distinct().size > 1 }.flatMap { it.points }.minOf { it.z }
        assertEquals(Estimates.DOOR_HEIGHT, lintelBottoms, 1e-9)
    }

    @Test
    fun windowHasSillLintelAndGlass() {
        val faces = Scene3D.build(Project(walls = listOf(wall), windows = listOf(PlanWindow("v", "w1", 200.0, 100.0)), wallHeight = 250.0))
        assertEquals(1, faces.count { it.kind == FaceKind.GLASS })
        assertEquals(5 * 4 + 1, faces.size)
    }

    @Test
    fun lowWallsCutTheModel() {
        val faces = Scene3D.build(Project(walls = listOf(wall), wallHeight = 250.0), height = 100.0)
        assertEquals(100.0, maxZ(faces), 1e-9)
    }

    @Test
    fun furnitureIsExtruded() {
        val item = Item("i", "wardrobe", 100.0, 100.0, 180.0, 60.0)
        val faces = Scene3D.build(Project(items = listOf(item)))
        assertEquals(210.0, maxZ(faces), 1e-9)
        assertTrue(faces.any { it.kind == FaceKind.ITEM_TOP })
    }

    @Test
    fun cameraProjectsTargetToCentre() {
        val cam = Camera(V3(100.0, 50.0, 0.0), yaw = 0.7, pitch = 0.6, distance = 800.0, focal = 500.0)
        val c = cam.project(V3(100.0, 50.0, 0.0))!!
        assertEquals(0.0, c.x, 1e-6)
        assertEquals(0.0, c.y, 1e-6)
        assertEquals(800.0, c.depth, 1e-6)
        // Higher points show higher on screen.
        assertTrue(cam.project(V3(100.0, 50.0, 100.0))!!.y < 0)
        assertNotNull(cam.project(V3(0.0, 0.0, 0.0)))
        // A point behind the camera is dropped.
        val behind = V3(100.0 + 2000 * Math.cos(0.6) * Math.cos(0.7), 50.0 - 2000 * Math.cos(0.6) * Math.sin(0.7), 2000 * Math.sin(0.6))
        assertNull(cam.project(behind))
    }

    @Test
    fun cameraIsNotMirrored() {
        // Looking from the south (plan bottom) with yaw -90°, plan x to the right stays right on screen.
        val cam = Camera(V3(0.0, 0.0, 0.0), yaw = -Math.PI / 2, pitch = 0.5, distance = 800.0, focal = 500.0)
        assertTrue(cam.project(V3(100.0, 0.0, 0.0))!!.x > 0)
        // And plan points further up (smaller y) are further away, so higher on screen.
        assertTrue(cam.project(V3(0.0, -100.0, 0.0))!!.y < cam.project(V3(0.0, 100.0, 0.0))!!.y)
    }
}
