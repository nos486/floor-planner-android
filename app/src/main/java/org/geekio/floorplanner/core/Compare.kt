package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall

/** A room's area before and after; null where the room doesn't exist on that side. */
data class RoomChange(val name: String, val before: Double?, val after: Double?)

/** What changed between two plans of the same floor. */
data class PlanDiff(
    val removedWalls: List<Wall>,
    val addedWalls: List<Wall>,
    val areaBefore: Double,
    val areaAfter: Double,
    val rooms: List<RoomChange>,
) {
    val changedRooms get() = rooms.filter { it.before == null || it.after == null || Math.abs(it.before - it.after) >= 0.01 }
}

object Compare {
    private const val TOLERANCE = 1.0

    private fun same(a: Wall, b: Wall): Boolean {
        val ends = (a.a.dist(b.a) <= TOLERANCE && a.b.dist(b.b) <= TOLERANCE) ||
            (a.a.dist(b.b) <= TOLERANCE && a.b.dist(b.a) <= TOLERANCE)
        return ends && Math.abs(a.thickness - b.thickness) < 0.5 && Math.abs(Math.abs(a.bulge) - Math.abs(b.bulge)) < 1
    }

    fun diff(before: Project, after: Project): PlanDiff {
        val removed = before.walls.filter { w -> after.walls.none { same(it, w) } }
        val added = after.walls.filter { w -> before.walls.none { same(it, w) } }
        val names = (before.rooms.map { it.name } + after.rooms.map { it.name }).distinct()
        val rooms = names.map { n ->
            RoomChange(n, before.rooms.filter { it.name == n }.takeIf { it.isNotEmpty() }?.sumOf { it.areaM2 },
                after.rooms.filter { it.name == n }.takeIf { it.isNotEmpty() }?.sumOf { it.areaM2 })
        }
        return PlanDiff(removed, added, before.totalAreaM2, after.totalAreaM2, rooms)
    }
}
