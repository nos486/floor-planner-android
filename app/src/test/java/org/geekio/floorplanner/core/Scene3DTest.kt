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

    @Test
    fun furnitureIsPaintedOverTheWallBehindIt() {
        // A long wall along y = 0 and a bed in front of its left end, seen from the south.
        val long = Wall.create("w", P(0.0, 0.0), P(800.0, 0.0), 20.0)
        val bed = Item("b", "bed_double", 100.0, 150.0, 160.0, 200.0)
        val faces = Scene3D.split(Scene3D.build(Project(walls = listOf(long), items = listOf(bed), wallHeight = 270.0)))
        val cam = Camera(V3(400.0, 100.0, 0.0), yaw = -Math.PI / 2, pitch = 0.5, distance = 900.0, focal = 500.0)
        val order = Scene3D.paintOrder(faces, cam)
        fun box(pts: List<Projected>) = listOf(pts.minOf { it.x }, pts.maxOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.y })
        fun overlap(a: List<Double>, b: List<Double>) = a[0] < b[1] && b[0] < a[1] && a[2] < b[3] && b[2] < a[3]
        val behindBed = order.withIndex().filter { (_, f) ->
            f.first.kind == FaceKind.WALL && f.first.points.all { it.x in 20.0..180.0 && it.y > 0 }
        }
        val bedFaces = order.withIndex().filter { it.value.first.kind == FaceKind.ITEM || it.value.first.kind == FaceKind.ITEM_TOP }
        assertTrue(behindBed.isNotEmpty())
        assertTrue(bedFaces.isNotEmpty())
        // A wall piece that shares screen space with a bed face must be painted before it.
        val late = behindBed.filter { w -> bedFaces.any { b -> b.index < w.index && overlap(box(b.value.second), box(w.value.second)) } }
            .map { w -> w.value.first.points.map { p -> "(${p.x.toInt()},${p.y.toInt()},${p.z.toInt()})" } }
        assertTrue("wall pieces painted over the bed: $late", late.isEmpty())
    }

    @Test
    fun splitKeepsTheArea() {
        val faces = Scene3D.build(Project(walls = listOf(wall), wallHeight = 250.0))
        val pieces = Scene3D.split(faces)
        assertTrue(pieces.size > faces.size)
        assertEquals(250.0, maxZ(pieces), 1e-9)
        // Each long side (400 x 250) becomes 8 x 4 pieces.
        assertEquals(32, pieces.count { f -> f.kind == FaceKind.WALL && f.points.all { it.y > 0 } })
    }

    @Test
    fun wallCabinetHangsAboveTheFloor() {
        val cabinet = Item("c", "wall_cabinet", 100.0, 30.0, 60.0, 35.0)
        val faces = Scene3D.build(Project(items = listOf(cabinet)))
        assertEquals(145.0, faces.flatMap { it.points }.minOf { it.z }, 1e-9)
        assertEquals(215.0, maxZ(faces), 1e-9)
    }

    @Test
    fun facesTurnedAwayAreNotPainted() {
        // Seen from the south, the north side of a box (outward -y) is hidden.
        val box = Item("b", "base_cabinet", 0.0, 0.0, 60.0, 60.0)
        val faces = Scene3D.build(Project(items = listOf(box)))
        val cam = Camera(V3(0.0, 0.0, 0.0), yaw = -Math.PI / 2, pitch = 0.5, distance = 800.0, focal = 500.0)
        val painted = Scene3D.paintOrder(faces, cam).map { it.first }
        assertTrue(painted.none { f -> f.kind == FaceKind.ITEM && f.points.all { it.y < -29 } })
        assertTrue(painted.any { f -> f.kind == FaceKind.ITEM && f.points.all { it.y > 29 } })
    }
}
