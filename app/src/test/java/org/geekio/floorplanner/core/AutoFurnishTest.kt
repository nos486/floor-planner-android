package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFurnishTest {
    private val plan = Templates.build("three_bed", "t") { it.name }!!
    private val placing = setOf(IssueKind.DOOR_BLOCKED, IssueKind.ITEMS_OVERLAP, IssueKind.ITEM_IN_WALL, IssueKind.WINDOW_BLOCKED)

    private fun furnishAll(p0: Project): Project {
        var p = p0
        for (room in p0.rooms) {
            val key = RoomKey.entries.firstOrNull { it.name == room.name } ?: continue
            p = AutoFurnish.furnish(p, room.id, key).project
        }
        return p
    }

    @Test
    fun everyRoomGetsItsMainPiecesWithoutProblems() {
        val p = furnishAll(plan)
        for (room in plan.rooms) {
            val key = RoomKey.entries.firstOrNull { it.name == room.name } ?: continue
            val inside = p.items.filter { pointInPolygon(P(it.x, it.y), room.polygon) }.map { it.type }
            val main = when (key) {
                RoomKey.BEDROOM -> listOf("bed_double", "bed_single")
                RoomKey.MASTER_BEDROOM -> listOf("bed_double")
                RoomKey.LIVING -> listOf("sofa3")
                RoomKey.KITCHEN -> listOf("kitchen_sink")
                RoomKey.BATHROOM, RoomKey.WC -> listOf("toilet")
                else -> continue
            }
            assertTrue("$key got $inside", inside.any { it in main })
        }
        assertEquals(emptyList<Issue>(), PlanCheck.run(p).filter { it.kind in placing })
    }

    @Test
    fun runningTwiceAddsNothing() {
        val once = furnishAll(plan)
        assertEquals(once.items.size, furnishAll(once).items.size)
    }

    @Test
    fun nightstandsFlankTheBed() {
        val room = plan.rooms.first { it.name == RoomKey.MASTER_BEDROOM.name }
        val r = AutoFurnish.furnish(plan, room.id, RoomKey.MASTER_BEDROOM)
        val bed = r.added.first { it.type == "bed_double" }
        val stands = r.added.filter { it.type == "nightstand" }
        assertEquals(2, stands.size)
        for (s in stands) assertEquals(bed.rotation, s.rotation, 1e-9)
    }
}
