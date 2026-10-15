package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Project
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class IssueKind { DOOR_BLOCKED, ITEMS_OVERLAP, ITEM_IN_WALL, WINDOW_BLOCKED, NARROW_ROOM, NO_DOOR, NO_WINDOW }

/**
 * A problem found in the plan, shown at [at]. [subject] and [other] are furniture types or a room name,
 * [value] a length in world units, depending on [kind].
 */
data class Issue(val kind: IssueKind, val at: P, val subject: String = "", val other: String = "", val value: Double = 0.0)

/** "Lint for homes": doors hitting furniture, furniture in walls or in front of windows, tight rooms and so on. */
object PlanCheck {
    /** Narrowest comfortable passage, in cm. */
    const val MIN_WIDTH_CM = 90.0
    /** Rooms smaller than this don't need a window (bathrooms, storage). */
    const val WINDOW_AREA_M2 = 6.0
    private val tallTypes = setOf("wardrobe", "bookshelf", "fridge")

    fun run(p: Project): List<Issue> {
        val floor = Floors.current(p)
        val upm = p.scale.unitsPerMeter
        val furniture = p.items.filter { !Furniture.isFixture(it) }
        val out = mutableListOf<Issue>()

        // Doors swinging into furniture
        for (door in p.doors) {
            val wall = p.wall(door.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, door) ?: continue
            for (leaf in PlanOps.doorLeaves(g, door)) {
                val a0 = Math.atan2(leaf.strike.y - leaf.hinge.y, leaf.strike.x - leaf.hinge.x)
                val a1 = Math.atan2(leaf.leafEnd.y - leaf.hinge.y, leaf.leafEnd.x - leaf.hinge.x)
                var sweep = a1 - a0
                while (sweep > Math.PI) sweep -= 2 * Math.PI
                while (sweep < -Math.PI) sweep += 2 * Math.PI
                val hit = furniture.firstOrNull { item ->
                    (1..5).any { ri ->
                        val r = leaf.radius * ri / 5
                        (0..9).any { ai ->
                            val a = a0 + sweep * ai / 9
                            Furniture.contains(item, P(leaf.hinge.x + cos(a) * r, leaf.hinge.y + sin(a) * r))
                        }
                    }
                }
                if (hit != null) out += Issue(IssueKind.DOOR_BLOCKED, g.center, hit.type)
            }
        }

        // Furniture overlapping other furniture
        val solid = furniture.filter { it.type !in setOf("plant") }
        for (i in solid.indices) for (j in i + 1 until solid.size) {
            if (overlap(shrunk(solid[i]), shrunk(solid[j]))) {
                out += Issue(IssueKind.ITEMS_OVERLAP, (P(solid[i].x, solid[i].y) + P(solid[j].x, solid[j].y)) * 0.5, solid[i].type, solid[j].type)
            }
        }

        // Furniture pushed into a wall (columns may sit in walls)
        val structural = setOf("column_square", "column_round")
        for (item in furniture.filter { it.type !in structural }) {
            val pts = shrunk(item).let { c -> c + c.indices.map { (c[it] + c[(it + 1) % c.size]) * 0.5 } }
            if (p.walls.any { w -> pts.any { Walls.distance(w, it) < w.thickness / 2 - 1 } }) {
                out += Issue(IssueKind.ITEM_IN_WALL, P(item.x, item.y), item.type)
            }
        }

        // Tall furniture in front of a window
        for (win in p.windows) {
            val wall = p.wall(win.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, win) ?: continue
            val samples = (0..6).map { g.start + (g.end - g.start) * (it / 6.0) }
            val hit = furniture.firstOrNull { it.type in tallTypes && samples.any { s -> Furniture.contains(it, s, wall.thickness / 2 + 15) } }
            if (hit != null) out += Issue(IssueKind.WINDOW_BLOCKED, g.center, hit.type)
        }

        // Rooms
        val hasDoors = p.doors.isNotEmpty()
        for (room in p.rooms) {
            val poly = room.polygon
            val c = room.center ?: continue
            if (poly.size < 3) continue
            val width = minWidth(poly)
            if (width < MIN_WIDTH_CM * upm / 100) out += Issue(IssueKind.NARROW_ROOM, c, room.name, value = width)
            if (hasDoors && p.doors.none { Estimates.onBoundary(floor, it, poly) }) out += Issue(IssueKind.NO_DOOR, c, room.name)
            if (room.areaM2 >= WINDOW_AREA_M2 && p.windows.none { Estimates.onBoundary(floor, it, poly) }) {
                out += Issue(IssueKind.NO_WINDOW, c, room.name)
            }
        }
        return out
    }

    /** Footprint pulled in by 2 cm so pieces that only touch don't count as overlapping. */
    private fun shrunk(item: Item): List<P> =
        Furniture.corners(item.copy(width = maxOf(1.0, item.width - 4), depth = maxOf(1.0, item.depth - 4)))

    /** Separating-axis test for two convex polygons. */
    fun overlap(a: List<P>, b: List<P>): Boolean {
        for (poly in listOf(a, b)) {
            for (i in poly.indices) {
                val axis = (poly[(i + 1) % poly.size] - poly[i]).perp()
                val pa = a.map { it.dot(axis) }
                val pb = b.map { it.dot(axis) }
                if (pa.max() <= pb.min() || pb.max() <= pa.min()) return false
            }
        }
        return true
    }

    /** Width of a room: for each side, how far the room reaches across from it; the smallest of those. */
    fun minWidth(poly: List<P>): Double = poly.indices.minOf { i ->
        val a = poly[i]
        val n = (poly[(i + 1) % poly.size] - a).normalized().perp()
        poly.maxOf { abs((it - a).dot(n)) }
    }

}
