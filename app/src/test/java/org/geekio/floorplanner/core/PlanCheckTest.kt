package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCheckTest {
    private fun wall(id: String, x1: Double, y1: Double, x2: Double, y2: Double) = Wall.create(id, P(x1, y1), P(x2, y2), 20.0)
    private val box = listOf(
        wall("w1", 0.0, 0.0, 400.0, 0.0), wall("w2", 400.0, 0.0, 400.0, 300.0),
        wall("w3", 400.0, 300.0, 0.0, 300.0), wall("w4", 0.0, 300.0, 0.0, 0.0),
    )

    private fun kinds(p: Project) = PlanCheck.run(PlanOps.withRooms(p)).map { it.kind }

    @Test
    fun goodRoomIsClean() {
        val p = Project(
            walls = box,
            // The door on the top wall swings down into the room (+y).
            doors = listOf(Door("d", "w1", 100.0, 90.0)),
            windows = listOf(PlanWindow("v", "w3", 200.0, 120.0)),
            items = listOf(Item("bed", "bed_double", 300.0, 150.0, 160.0, 200.0)),
        )
        assertEquals(emptyList<IssueKind>(), kinds(p))
    }

    @Test
    fun findsProblems() {
        val p = Project(
            walls = box,
            doors = listOf(Door("d", "w1", 100.0, 90.0)),
            items = listOf(
                // In the door's swing.
                Item("a", "armchair", 100.0, 60.0, 80.0, 80.0),
                // Two tables on top of each other, one poking into the right wall.
                Item("t1", "coffee_table", 300.0, 200.0, 110.0, 60.0),
                Item("t2", "coffee_table", 340.0, 200.0, 110.0, 60.0),
            ),
        )
        val k = kinds(p)
        assertTrue(IssueKind.DOOR_BLOCKED in k)
        assertTrue(IssueKind.ITEMS_OVERLAP in k)
        assertTrue(IssueKind.ITEM_IN_WALL in k)
        // 12 m² and no window.
        assertTrue(IssueKind.NO_WINDOW in k)
    }

    @Test
    fun narrowRoomAndMissingDoor() {
        val walls = box + wall("w5", 0.0, 80.0, 400.0, 80.0)
        val p = Project(walls = walls, doors = listOf(Door("d", "w3", 200.0, 90.0)), windows = listOf(PlanWindow("v", "w3", 80.0, 60.0)))
        val issues = PlanCheck.run(PlanOps.withRooms(p))
        val narrow = issues.single { it.kind == IssueKind.NARROW_ROOM }
        assertEquals(80.0, narrow.value, 1e-6)
        // The 80 cm strip has no door.
        assertTrue(issues.any { it.kind == IssueKind.NO_DOOR })
    }

    @Test
    fun tallFurnitureBlocksWindow() {
        val p = Project(
            walls = box,
            windows = listOf(PlanWindow("v", "w1", 200.0, 120.0)),
            items = listOf(Item("w", "wardrobe", 200.0, 40.0, 180.0, 60.0)),
        )
        assertTrue(IssueKind.WINDOW_BLOCKED in kinds(p))
    }
}
