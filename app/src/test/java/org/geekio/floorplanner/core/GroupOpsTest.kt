package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupOpsTest {
    private val box = listOf(
        Wall.create("w1", P(0.0, 0.0), P(400.0, 0.0), 20.0),
        Wall.create("w2", P(400.0, 0.0), P(400.0, 300.0), 20.0),
        Wall.create("w3", P(400.0, 300.0), P(0.0, 300.0), 20.0),
        Wall.create("w4", P(0.0, 300.0), P(0.0, 0.0), 20.0),
    )
    private val bed = Furniture.create(Furniture.type("bed_single")!!, P(100.0, 150.0))
    private val plan = PlanOps.withRooms(
        Project(walls = box, doors = listOf(Door("d1", "w1", 200.0)), items = listOf(bed))
    )
    private val all = GroupOps.inBox(plan, P(-50.0, -50.0), P(450.0, 350.0))

    @Test
    fun boxSelectsWallsAndItemsInside() {
        assertEquals(5, all.size)
        // Only the furniture fits a small box.
        assertEquals(setOf(Selection(Kind.ITEM, bed.id)), GroupOps.inBox(plan, P(40.0, 40.0), P(160.0, 260.0)))
    }

    @Test
    fun doorStandsForItsWallAndRoomsCannotBeGrouped() {
        assertEquals(Selection(Kind.WALL, "w1"), GroupOps.member(plan, Selection(Kind.DOOR, "d1")))
        assertNull(GroupOps.member(plan, Selection(Kind.ROOM, plan.rooms.single().id)))
    }

    @Test
    fun translateMovesEverythingAndKeepsTheRoom() {
        val p = GroupOps.translate(plan, all, P(100.0, 50.0))
        assertEquals(P(100.0, 50.0), p.wall("w1")!!.a)
        assertEquals(200.0, p.item(bed.id)!!.x, 1e-9)
        assertEquals(12.0, p.rooms.single().areaM2, 1e-9)
    }

    @Test
    fun rotateTurnsAroundTheCentre() {
        val p = GroupOps.rotate90(plan, all)
        val pts = GroupOps.points(p, all)
        // A 400 x 300 box becomes 300 x 400.
        val walls = p.walls.flatMap { listOf(it.a, it.b) }
        assertEquals(300.0, walls.maxOf { it.x } - walls.minOf { it.x }, 1e-9)
        assertEquals(400.0, walls.maxOf { it.y } - walls.minOf { it.y }, 1e-9)
        assertEquals(90.0, p.item(bed.id)!!.rotation, 1e-9)
        assertTrue(pts.isNotEmpty())
        assertEquals(12.0, p.rooms.single().areaM2, 1e-9)
    }

    @Test
    fun mirrorFlipsDoorsAndFurniture() {
        val turned = PlanOps.rotateItem(plan, bed.id, 30.0)
        val p = GroupOps.mirror(turned, all)
        assertEquals(300.0, p.item(bed.id)!!.x, 1e-9)
        assertEquals(330.0, p.item(bed.id)!!.rotation, 1e-9)
        assertEquals(-1, p.door("d1")!!.side)
    }

    @Test
    fun duplicateCopiesWallsWithTheirOpenings() {
        val (p, copies) = GroupOps.duplicate(plan, all, P(500.0, 0.0))
        assertEquals(8, p.walls.size)
        assertEquals(2, p.doors.size)
        assertEquals(2, p.items.size)
        assertEquals(2, p.rooms.size)
        assertEquals(5, copies.size)
        assertTrue(copies.none { it in all })
        assertEquals(copies, GroupOps.existing(p, copies))
    }

    @Test
    fun deleteRemovesMembersAndTheirOpenings() {
        val p = GroupOps.delete(plan, all)
        assertTrue(p.walls.isEmpty() && p.doors.isEmpty() && p.items.isEmpty() && p.rooms.isEmpty())
    }

    @Test
    fun lockedItemStaysWhenTheGroupMoves() {
        val locked = PlanOps.updateItem(plan, bed.id) { it.copy(locked = true) }
        val moved = GroupOps.translate(locked, all, P(50.0, 0.0))
        assertEquals(100.0, moved.item(bed.id)!!.x, 1e-9)
        assertEquals(50.0, moved.walls.first().a.x, 1e-9)
        // A copy of a locked item can be moved.
        val (copied, _) = PlanOps.duplicateItem(locked, bed.id)!!
        assertTrue(!copied.items.last().locked)
    }
}
