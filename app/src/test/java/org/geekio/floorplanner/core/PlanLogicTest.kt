package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLogicTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) =
        Wall.create(id, P(x1, y1), P(x2, y2), 20.0)

    /** 4 m x 3 m rectangle. */
    private val box = listOf(
        wall("w1", 0.0, 0.0, 400.0, 0.0),
        wall("w2", 400.0, 0.0, 400.0, 300.0),
        wall("w3", 400.0, 300.0, 0.0, 300.0),
        wall("w4", 0.0, 300.0, 0.0, 0.0),
    )

    @Test
    fun detectsSingleRoomArea() {
        val rooms = RoomDetector.detect(box, org.geekio.floorplanner.model.Scale())
        assertEquals(1, rooms.size)
        assertEquals(12.0, rooms[0].areaM2, 0.001)
        assertEquals("Room 1", rooms[0].name)
    }

    @Test
    fun wallDrawnInReverseOrderStillDetected() {
        val reversed = box.map { Wall.create(it.id, it.b, it.a, 20.0) }.reversed()
        assertEquals(12.0, RoomDetector.detect(reversed, org.geekio.floorplanner.model.Scale()).single().areaM2, 0.001)
    }

    @Test
    fun interiorWallWithTJunctionsSplitsIntoTwoRooms() {
        val walls = box + wall("w5", 150.0, 0.0, 150.0, 300.0)
        val rooms = RoomDetector.detect(walls, org.geekio.floorplanner.model.Scale())
        assertEquals(2, rooms.size)
        assertEquals(listOf(4.5, 7.5), rooms.map { it.areaM2 })
    }

    @Test
    fun crossingWallsAreSplit() {
        // A plus-shaped pair of walls through the box: 4 rooms.
        val walls = box + wall("w5", 200.0, -50.0, 200.0, 350.0) + wall("w6", -50.0, 150.0, 450.0, 150.0)
        val rooms = RoomDetector.detect(walls, org.geekio.floorplanner.model.Scale())
        assertEquals(4, rooms.size)
        rooms.forEach { assertEquals(3.0, it.areaM2, 0.001) }
    }

    @Test
    fun openShapeHasNoRooms() {
        assertTrue(RoomDetector.detect(box.dropLast(1), org.geekio.floorplanner.model.Scale()).isEmpty())
    }

    @Test
    fun roomNamesSurviveEdits() {
        var p = PlanOps.withRooms(Project(walls = box))
        p = PlanOps.renameRoom(p, p.rooms[0].id, "Kitchen")
        p = PlanOps.moveVertex(p, P(400.0, 300.0), P(500.0, 300.0))
        assertEquals("Kitchen", p.rooms.single().name)
        assertTrue(p.rooms.single().areaM2 > 12.0)
    }

    @Test
    fun moveVertexKeepsJunctionConnected() {
        val p = PlanOps.moveVertex(Project(walls = box), P(400.0, 0.0), P(420.0, -20.0))
        assertEquals(P(420.0, -20.0), p.wall("w1")!!.b)
        assertEquals(P(420.0, -20.0), p.wall("w2")!!.a)
    }

    @Test
    fun translateWallDragsConnectedEnds() {
        val p = PlanOps.translateWall(PlanOps.withRooms(Project(walls = box)), "w2", P(100.0, 0.0))
        assertEquals(P(500.0, 0.0), p.wall("w1")!!.b)
        assertEquals(P(500.0, 300.0), p.wall("w3")!!.a)
        assertEquals(15.0, p.rooms.single().areaM2, 0.001)
    }

    @Test
    fun setWallLength() {
        val p = PlanOps.setWallLength(Project(walls = box), "w1", 500.0)
        assertEquals(500.0, p.wall("w1")!!.length, 0.001)
        assertEquals(P(500.0, 0.0), p.wall("w2")!!.a)
    }

    @Test
    fun splitWallMovesOpenings() {
        val base = Project(walls = box, doors = listOf(Door("d1", "w1", 300.0, 90.0)))
        val (p, firstId) = PlanOps.splitWall(base, "w1")!!
        assertEquals(5, p.walls.size)
        val door = p.door("d1")!!
        assertTrue(door.wallId != firstId)
        assertEquals(100.0, door.offset, 0.001)
        assertEquals(1, p.rooms.size)
    }

    @Test
    fun deleteWallRemovesItsOpenings() {
        val base = Project(walls = box, doors = listOf(Door("d1", "w1", 200.0)))
        val p = PlanOps.deleteWall(base, "w1")
        assertTrue(p.doors.isEmpty())
        assertTrue(p.rooms.isEmpty())
    }

    @Test
    fun openingOffsetIsClampedInsideWall() {
        assertEquals(45.0, PlanOps.openingOffset(box[0], P(-30.0, 5.0), 90.0)!!, 0.001)
        assertEquals(355.0, PlanOps.openingOffset(box[0], P(399.0, 5.0), 90.0)!!, 0.001)
        assertNull(PlanOps.openingOffset(wall("s", 0.0, 0.0, 50.0, 0.0), P(10.0, 0.0), 90.0))
    }

    @Test
    fun doorSwingGeometry() {
        val g = PlanOps.openingGeometry(box[0], Door("d", "w1", 200.0, 90.0))!!
        assertEquals(P(155.0, 0.0), g.start)
        val swing = PlanOps.doorSwing(g, Door("d", "w1", 200.0, 90.0))
        assertEquals(P(155.0, 90.0), swing.leafEnd)
        assertTrue(swing.clockwise)
        val flipped = PlanOps.doorSwing(g, Door("d", "w1", 200.0, 90.0, flipped = true, side = -1))
        assertEquals(P(245.0, -90.0), flipped.leafEnd)
        assertTrue(flipped.clockwise)
    }

    @Test
    fun hitTesting() {
        val p = PlanOps.withRooms(Project(walls = box, doors = listOf(Door("d1", "w1", 200.0))))
        assertEquals(Selection(Kind.DOOR, "d1"), PlanOps.hit(p, P(200.0, 3.0), 10.0))
        assertEquals(Selection(Kind.WALL, "w2"), PlanOps.hit(p, P(405.0, 150.0), 10.0))
        assertEquals(Kind.ROOM, PlanOps.hit(p, P(200.0, 150.0), 10.0)!!.kind)
        assertNull(PlanOps.hit(p, P(900.0, 900.0), 10.0))
    }

    @Test
    fun snapping() {
        val opts = SnapOptions()
        assertEquals(P(400.0, 0.0), Snapper.resolve(P(392.0, 7.0), box, 20.0, 15.0, opts).point)
        assertEquals(P(60.0, 40.0), Snapper.resolve(P(55.0, 47.0), emptyList(), 20.0, 15.0, opts).point)
        val angled = Snapper.resolve(P(100.0, 6.0), emptyList(), 20.0, 15.0, opts, start = P(0.0, 0.0))
        assertEquals(SnapType.ANGLE, angled.type)
        assertEquals(P(100.0, 0.0), angled.point)
    }

    @Test
    fun readsWebAppJson() {
        val text = """
            {"projectName":"My Home Plan","scale":{"unitsPerMeter":100,"gridCm":20},
             "walls":[{"id":"w_1","points":[[0,0],[400,0]],"thickness":20},
                      {"id":"w_2","points":[[400,0],[400,300]],"thickness":20},
                      {"id":"w_3","points":[[400,300],[0,300]],"thickness":20},
                      {"id":"w_4","points":[[0,300],[0,0]],"thickness":20}],
             "doors":[{"id":"d_1","wallId":"w_1","offset":80,"width":90,"flipped":false,"side":1},
                      {"id":"d_2","wallId":"missing","offset":80,"width":90}],
             "windows":[{"id":"win_1","wallId":"w_1","offset":200,"width":120}],
             "rooms":[{"id":"room_1","name":"Living Room","areaM2":12.0,"centroid":[200,150]}],
             "extra":true}
        """.trimIndent()
        val p = ProjectIO.fromJson(text)
        assertNotNull(p)
        p!!
        assertEquals(4, p.walls.size)
        assertEquals(1, p.doors.size)
        assertEquals("Living Room", p.rooms.single().name)
        assertEquals("room_1", p.rooms.single().id)
        val again = ProjectIO.fromJson(ProjectIO.toJson(p))
        assertEquals(p, again)
        assertNull(ProjectIO.fromJson("not json"))
    }

    @Test
    fun svgExportContainsElements() {
        val p = PlanOps.withRooms(Project(walls = box, doors = listOf(Door("d1", "w1", 200.0))))
        val svg = ProjectIO.toSvg(p)
        assertTrue(svg.startsWith("<svg"))
        assertEquals(4 + 2, Regex("<line").findAll(svg).count())
        assertTrue(svg.contains("Room 1"))
        assertTrue(svg.contains("12.00 m²"))
    }
}
