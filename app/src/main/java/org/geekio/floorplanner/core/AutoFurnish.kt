package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Room
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** What [AutoFurnish.furnish] did: the items it added and the types it found no room for. */
data class FurnishResult(val project: Project, val added: List<Item>, val skipped: List<String>)

/**
 * Rule-based furnishing of one room, fully offline: a standard set of furniture for the kind of room, each
 * piece tried along the walls (or in the open for dining tables) and kept only where it fits inside the room,
 * clears the doorways and door swings, and doesn't overlap anything already there.
 */
object AutoFurnish {
    private enum class Spot {
        /** Back against a wall, as near the middle of a long wall as it fits. */
        MIDDLE,
        /** Back against a wall, as near a corner as it fits. */
        CORNER,
        /** On both sides of the [Want.anchor] piece, against the same wall. */
        BESIDE,
        /** In front of the [Want.anchor] piece, facing the same way. */
        FRONT,
        /** Against a wall, turned towards the [Want.anchor] piece. */
        FACING,
        /** Free-standing near the middle of the room. */
        OPEN,
    }

    private data class Want(val type: String, val spot: Spot, val anchor: String? = null, val count: Int = 1)

    /** Room for walking in front of a doorway and around a free-standing table, in cm. */
    private const val PASSAGE = 60.0
    private const val STEP = 10.0

    private fun wants(key: RoomKey, areaM2: Double): List<Want> = when (key) {
        RoomKey.BEDROOM -> {
            val bed = if (areaM2 >= 10) "bed_double" else "bed_single"
            listOf(Want(bed, Spot.MIDDLE), Want("nightstand", Spot.BESIDE, bed, 2), Want("wardrobe", Spot.CORNER), Want("desk", Spot.CORNER))
        }
        RoomKey.MASTER_BEDROOM -> listOf(
            Want("bed_double", Spot.MIDDLE), Want("nightstand", Spot.BESIDE, "bed_double", 2),
            Want("wardrobe", Spot.CORNER), Want("dressing_table", Spot.CORNER),
        )
        RoomKey.LIVING -> listOf(
            Want("sofa3", Spot.MIDDLE), Want("coffee_table", Spot.FRONT, "sofa3"), Want("tv_stand", Spot.FACING, "sofa3"),
            Want("armchair", Spot.CORNER, count = 2), Want("plant", Spot.CORNER), Want("dining6", Spot.OPEN),
        )
        RoomKey.FAMILY -> listOf(
            Want("sofa2", Spot.MIDDLE), Want("coffee_table", Spot.FRONT, "sofa2"), Want("tv_stand", Spot.FACING, "sofa2"),
            Want("armchair", Spot.CORNER),
        )
        RoomKey.KITCHEN -> listOf(
            Want("kitchen_sink", Spot.MIDDLE), Want("stove", Spot.MIDDLE), Want("fridge", Spot.CORNER),
            Want("base_cabinet", Spot.MIDDLE, count = 4), Want("dining4", Spot.OPEN),
        )
        RoomKey.BATHROOM -> listOf(
            Want(if (areaM2 >= 5) "bathtub" else "shower", Spot.CORNER), Want("toilet", Spot.CORNER), Want("basin", Spot.MIDDLE),
        )
        RoomKey.WC -> listOf(Want("toilet", Spot.MIDDLE), Want("basin", Spot.CORNER))
        RoomKey.STORAGE -> listOf(Want("bookshelf", Spot.CORNER, count = 3))
        RoomKey.STUDY -> listOf(Want("desk", Spot.MIDDLE), Want("bookshelf", Spot.CORNER), Want("armchair", Spot.CORNER))
    }

    fun furnish(p: Project, roomId: String, key: RoomKey): FurnishResult {
        val room = p.room(roomId) ?: return FurnishResult(p, emptyList(), emptyList())
        val poly = room.polygon
        if (poly.size < 3) return FurnishResult(p, emptyList(), emptyList())
        val fit = Fit(p, room)
        var project = p
        val added = mutableListOf<Item>()
        val skipped = mutableListOf<String>()
        for (want in wants(key, room.areaM2)) {
            val type = Furniture.type(want.type) ?: continue
            // Pieces already in the room count towards what is wanted, so running it twice adds nothing new.
            val have = project.items.count { it.type == want.type && fit.inRoom(it) }
            repeat(want.count - have) {
                val item = place(project, fit, want, Furniture.create(type, P.ZERO))
                if (item == null) {
                    skipped += want.type
                } else {
                    project = project.copy(items = project.items + item)
                    added += item
                }
            }
        }
        return FurnishResult(project, added, skipped.distinct())
    }

    private fun place(p: Project, fit: Fit, want: Want, proto: Item): Item? {
        val anchor = want.anchor?.let { a -> p.items.lastOrNull { it.type == a && fit.inRoom(it) } }
        val candidates: List<Item> = when (want.spot) {
            Spot.MIDDLE -> fit.alongWalls(proto).sortedBy { it.second }.map { it.first }
            Spot.CORNER -> fit.alongWalls(proto, corner = true).sortedBy { it.second }.map { it.first }
            Spot.BESIDE -> {
                if (anchor == null) return null
                listOf(-1.0, 1.0).map { side ->
                    val local = P(side * (anchor.width / 2 + proto.width / 2 + 2), -anchor.depth / 2 + proto.depth / 2)
                    val c = Furniture.toWorld(anchor, local)
                    proto.copy(x = roundTo(c.x, 1), y = roundTo(c.y, 1), rotation = anchor.rotation)
                }
            }
            Spot.FRONT -> {
                if (anchor == null) return null
                val c = Furniture.toWorld(anchor, P(0.0, anchor.depth / 2 + 40 + proto.depth / 2))
                listOf(proto.copy(x = roundTo(c.x, 1), y = roundTo(c.y, 1), rotation = anchor.rotation))
            }
            Spot.FACING -> {
                if (anchor == null) return null
                val target = P(anchor.x, anchor.y)
                fit.alongWalls(proto).map { (item, _) ->
                    val front = Furniture.toWorld(item, P(0.0, 1.0)) - P(item.x, item.y)
                    val to = (target - P(item.x, item.y)).normalized()
                    item to front.dot(to)
                }.filter { it.second > 0.9 }.sortedByDescending { it.second }.map { it.first }
            }
            Spot.OPEN -> fit.inTheOpen(proto)
        }
        return candidates.firstOrNull { fit.ok(p, it, clearance = if (want.spot == Spot.OPEN) PASSAGE else 0.0) }
    }

    /** Geometry of one room used to test where furniture can go. */
    private class Fit(p: Project, val room: Room) {
        val poly = room.polygon
        private val walls = p.walls
        private val centre = room.center ?: P(poly.sumOf { it.x } / poly.size, poly.sumOf { it.y } / poly.size)
        private val longest = poly.indices.maxOf { poly[it].dist(poly[(it + 1) % poly.size]) }

        /** Doorway zones: a square the door's width deep on both sides of every door on this room's walls. */
        private val doorways: List<List<P>> = p.doors.mapNotNull { d ->
            val wall = p.wall(d.wallId) ?: return@mapNotNull null
            val g = PlanOps.openingGeometry(wall, d) ?: return@mapNotNull null
            val reach = maxOf(d.width, PASSAGE) + g.wallThickness / 2
            listOf(g.start - g.normal * reach, g.end - g.normal * reach, g.end + g.normal * reach, g.start + g.normal * reach)
        }

        fun inRoom(item: Item) = pointInPolygon(P(item.x, item.y), poly)

        /** Every spot with the item's back to one of the room's sides, with a score (lower is better). */
        fun alongWalls(proto: Item, corner: Boolean = false): List<Pair<Item, Double>> {
            val out = mutableListOf<Pair<Item, Double>>()
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                val len = a.dist(b)
                if (len < proto.width) continue
                val dir = (b - a).normalized()
                var n = dir.perp()
                if (!pointInPolygon((a + b) * 0.5 + n * 5.0, poly)) n = -n
                var t = proto.width / 2
                while (t <= len - proto.width / 2 + 0.01) {
                    val guess = a + dir * t + n * (proto.depth / 2 + 10)
                    Furniture.snapToWall(proto.copy(x = guess.x, y = guess.y), guess, walls, reach = 40.0)?.let { item ->
                        val fromEnd = minOf(t, len - t) - proto.width / 2
                        val score = if (corner) fromEnd + (longest - len) * 0.05 else abs(t - len / 2) + (longest - len) * 0.5
                        out += item to score
                    }
                    t += STEP
                }
            }
            return out
        }

        /** Free-standing spots on a grid, lined up with the room's longest side, nearest the middle first. */
        fun inTheOpen(proto: Item): List<Item> {
            val i = poly.indices.maxBy { poly[it].dist(poly[(it + 1) % poly.size]) }
            val e = poly[(i + 1) % poly.size] - poly[i]
            val angle = Furniture.normalizeAngle(Math.toDegrees(atan2(e.y, e.x)))
            val minX = poly.minOf { it.x }
            val maxX = poly.maxOf { it.x }
            val minY = poly.minOf { it.y }
            val maxY = poly.maxOf { it.y }
            val out = mutableListOf<Item>()
            var y = minY
            while (y <= maxY) {
                var x = minX
                while (x <= maxX) {
                    if (pointInPolygon(P(x, y), poly)) {
                        for (r in listOf(angle, Furniture.normalizeAngle(angle + 90))) out += proto.copy(x = roundTo(x, 1), y = roundTo(y, 1), rotation = r)
                    }
                    x += STEP
                }
                y += STEP
            }
            return out.sortedBy { hypot(it.x - centre.x, it.y - centre.y) }
        }

        /** Whether [item] fits: inside the room, out of doorways and off other furniture, with [clearance] around it. */
        fun ok(p: Project, item: Item, clearance: Double): Boolean {
            val outline = Furniture.corners(item)
            val pts = outline + outline.indices.map { (outline[it] + outline[(it + 1) % 4]) * 0.5 }
            if (!pts.all { pointInPolygon(it, poly) }) return false
            val roomy = if (clearance > 0) Furniture.corners(item.copy(width = item.width + 2 * clearance, depth = item.depth + 2 * clearance)) else outline
            if (clearance > 0 && !roomy.all { pointInPolygon(it, poly) }) return false
            val body = shrunk(item)
            if (doorways.any { PlanCheck.overlap(body, it) }) return false
            val flat = item.type in Furniture.flat
            val raised = item.type in Furniture.raised
            for (other in p.items) {
                if (Furniture.isFixture(other) || other.type in Furniture.flat || flat) continue
                if ((other.type in Furniture.raised) != raised) continue
                if (PlanCheck.overlap(if (clearance > 0) roomy else body, shrunk(other))) return false
            }
            // Leave the plan check (door swings, windows, walls) no worse than it was.
            val relevant = setOf(IssueKind.DOOR_BLOCKED, IssueKind.ITEM_IN_WALL, IssueKind.WINDOW_BLOCKED, IssueKind.ITEMS_OVERLAP)
            fun count(q: Project) = PlanCheck.run(q).count { it.kind in relevant }
            val before = baseline?.takeIf { it.first === p }?.second ?: count(p).also { baseline = p to it }
            return count(p.copy(items = p.items + item)) <= before
        }

        private var baseline: Pair<Project, Int>? = null

        private fun shrunk(item: Item) =
            Furniture.corners(item.copy(width = maxOf(1.0, item.width - 4), depth = maxOf(1.0, item.depth - 4)))
    }
}
