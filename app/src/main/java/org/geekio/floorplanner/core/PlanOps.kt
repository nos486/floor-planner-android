package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Opening
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall

/** Geometry of an opening (door/window) resolved against its wall. */
data class OpeningGeometry(
    val start: P,
    val end: P,
    val center: P,
    /** Unit vector along the wall. */
    val dir: P,
    /** Unit normal of the wall. */
    val normal: P,
    val wallThickness: Double,
)

/** Door swing drawing data: the leaf goes from [hinge] to [leafEnd], the arc from [strike] to [leafEnd]. */
data class DoorSwing(val hinge: P, val strike: P, val leafEnd: P, val radius: Double, val clockwise: Boolean)

enum class Kind { WALL, DOOR, WINDOW, ROOM, ITEM }

data class Selection(val kind: Kind, val id: String)

/** Pure editing operations on [Project]. None of them record history. */
object PlanOps {
    const val MIN_WALL_LENGTH = 5.0
    private const val JOINT_TOLERANCE = 0.5

    fun withRooms(p: Project): Project = p.copy(rooms = RoomDetector.detect(p.walls, p.scale, p.rooms))

    fun addWall(p: Project, a: P, b: P, thickness: Double): Pair<Project, Wall>? {
        if (a.dist(b) < MIN_WALL_LENGTH) return null
        val wall = Wall.create(generateId("w"), a, b, thickness)
        return withRooms(p.copy(walls = p.walls + wall)) to wall
    }

    /** Moves every wall endpoint that sits on [from] to [to] (keeps junctions connected). */
    fun moveVertex(p: Project, from: P, to: P): Project {
        val walls = p.walls.map { w ->
            val a = if (w.a.dist(from) <= JOINT_TOLERANCE) to else w.a
            val b = if (w.b.dist(from) <= JOINT_TOLERANCE) to else w.b
            if (a == w.a && b == w.b) w else w.withPoints(a, b)
        }
        return withRooms(p.copy(walls = walls))
    }

    /** Moves a whole wall by [delta]; endpoints of other walls joined to it follow. */
    fun translateWall(p: Project, wallId: String, delta: P): Project {
        val wall = p.wall(wallId) ?: return p
        val oa = wall.a
        val ob = wall.b
        val walls = p.walls.map { w ->
            fun move(pt: P) = when {
                pt.dist(oa) <= JOINT_TOLERANCE || pt.dist(ob) <= JOINT_TOLERANCE -> pt + delta
                else -> pt
            }
            if (w.id == wallId) w.withPoints(oa + delta, ob + delta) else {
                val a = move(w.a)
                val b = move(w.b)
                if (a == w.a && b == w.b) w else w.withPoints(a, b)
            }
        }
        return withRooms(p.copy(walls = walls))
    }

    /** Changes the wall length by moving its end point (and anything joined to it) along the wall. */
    fun setWallLength(p: Project, wallId: String, length: Double): Project {
        val wall = p.wall(wallId) ?: return p
        if (length < MIN_WALL_LENGTH) return p
        val dir = (wall.b - wall.a).normalized()
        if (dir == P.ZERO) return p
        return moveVertex(p, wall.b, (wall.a + dir * length).rounded())
    }

    fun setWallThickness(p: Project, wallId: String, thickness: Double): Project =
        p.copy(walls = p.walls.map { if (it.id == wallId) it.copy(thickness = thickness.coerceIn(5.0, 100.0)) else it })

    fun deleteWall(p: Project, wallId: String): Project = withRooms(
        p.copy(
            walls = p.walls.filter { it.id != wallId },
            doors = p.doors.filter { it.wallId != wallId },
            windows = p.windows.filter { it.wallId != wallId },
        )
    )

    /** Splits a wall in two at its middle. Returns the new project and the id of the first half. */
    fun splitWall(p: Project, wallId: String): Pair<Project, String>? {
        val idx = p.walls.indexOfFirst { it.id == wallId }
        if (idx < 0) return null
        val wall = p.walls[idx]
        val mid = ((wall.a + wall.b) * 0.5).rounded()
        val d1 = wall.a.dist(mid)
        if (d1 < 10 || mid.dist(wall.b) < 10) return null
        val wa = Wall.create(generateId("w"), wall.a, mid, wall.thickness)
        val wb = Wall.create(generateId("w"), mid, wall.b, wall.thickness)
        val walls = p.walls.toMutableList().apply { removeAt(idx); addAll(idx, listOf(wa, wb)) }
        val doors = p.doors.map {
            if (it.wallId != wallId) it
            else if (it.offset <= d1) it.copy(wallId = wa.id)
            else it.copy(wallId = wb.id, offset = roundTo(it.offset - d1, 1))
        }
        val windows = p.windows.map {
            if (it.wallId != wallId) it
            else if (it.offset <= d1) it.copy(wallId = wa.id)
            else it.copy(wallId = wb.id, offset = roundTo(it.offset - d1, 1))
        }
        return withRooms(p.copy(walls = walls, doors = doors, windows = windows)) to wa.id
    }

    /** Offset along [wall] for an opening of [width] centred as close as possible to [point]; null if it doesn't fit. */
    fun openingOffset(wall: Wall, point: P, width: Double): Double? {
        val len = wall.length
        if (len < width + 2) return null
        val proj = projectPointOnSegment(point, wall.a, wall.b)
        return roundTo((proj.t * len).coerceIn(width / 2, len - width / 2), 1)
    }

    fun addDoor(p: Project, wallId: String, offset: Double, width: Double): Pair<Project, Door> {
        val door = Door(generateId("d"), wallId, offset, width)
        return p.copy(doors = p.doors + door) to door
    }

    fun addWindow(p: Project, wallId: String, offset: Double, width: Double): Pair<Project, PlanWindow> {
        val win = PlanWindow(generateId("win"), wallId, offset, width)
        return p.copy(windows = p.windows + win) to win
    }

    fun updateDoor(p: Project, id: String, f: (Door) -> Door) = p.copy(doors = p.doors.map { if (it.id == id) f(it) else it })

    fun updateWindow(p: Project, id: String, f: (PlanWindow) -> PlanWindow) =
        p.copy(windows = p.windows.map { if (it.id == id) f(it) else it })

    /** Sets the width of an opening, clamped so it still fits in its wall. */
    fun setOpeningWidth(p: Project, kind: Kind, id: String, width: Double): Project {
        val opening: Opening = (if (kind == Kind.DOOR) p.door(id) else p.window(id)) ?: return p
        val wall = p.wall(opening.wallId) ?: return p
        val w = width.coerceIn(30.0, maxOf(30.0, wall.length - 2))
        val offset = opening.offset.coerceIn(w / 2, maxOf(w / 2, wall.length - w / 2))
        return if (kind == Kind.DOOR) updateDoor(p, id) { it.copy(width = w, offset = offset) }
        else updateWindow(p, id) { it.copy(width = w, offset = offset) }
    }

    fun renameRoom(p: Project, id: String, name: String) =
        p.copy(rooms = p.rooms.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })

    fun addItem(p: Project, item: Item): Project = p.copy(items = p.items + item)

    fun updateItem(p: Project, id: String, f: (Item) -> Item) = p.copy(items = p.items.map { if (it.id == id) f(it) else it })

    fun setItemSize(p: Project, id: String, width: Double, depth: Double) =
        updateItem(p, id) { it.copy(width = width.coerceIn(10.0, 1000.0), depth = depth.coerceIn(10.0, 1000.0)) }

    fun rotateItem(p: Project, id: String, degrees: Double) =
        updateItem(p, id) { it.copy(rotation = Furniture.normalizeAngle(it.rotation + degrees)) }

    /** Copies an item next to the original. Returns the new project and the copy. */
    fun duplicateItem(p: Project, id: String): Pair<Project, Item>? {
        val item = p.item(id) ?: return null
        val shift = Furniture.toWorld(item.copy(x = 0.0, y = 0.0), P(item.width + 20, 0.0))
        val copy = item.copy(id = generateId("it"), x = roundTo(item.x + shift.x, 1), y = roundTo(item.y + shift.y, 1))
        return p.copy(items = p.items + copy) to copy
    }

    fun delete(p: Project, sel: Selection): Project = when (sel.kind) {
        Kind.ITEM -> p.copy(items = p.items.filter { it.id != sel.id })
        Kind.WALL -> deleteWall(p, sel.id)
        Kind.DOOR -> p.copy(doors = p.doors.filter { it.id != sel.id })
        Kind.WINDOW -> p.copy(windows = p.windows.filter { it.id != sel.id })
        Kind.ROOM -> p
    }

    fun openingGeometry(wall: Wall, opening: Opening): OpeningGeometry? {
        val len = wall.length
        if (len == 0.0) return null
        val dir = (wall.b - wall.a) * (1 / len)
        val half = minOf(opening.width, len) / 2
        val offset = opening.offset.coerceIn(half, len - half)
        val center = wall.a + dir * offset
        return OpeningGeometry(center - dir * half, center + dir * half, center, dir, dir.perp(), wall.thickness)
    }

    fun doorSwing(g: OpeningGeometry, door: Door): DoorSwing {
        val hinge = if (door.flipped) g.end else g.start
        val strike = if (door.flipped) g.start else g.end
        val radius = g.start.dist(g.end)
        val swingDir = g.normal * (if (door.side < 0) -1.0 else 1.0)
        val along = (strike - hinge).normalized()
        return DoorSwing(hinge, strike, hinge + swingDir * radius, radius, along.cross(swingDir) > 0)
    }

    // --- Hit testing (all values in world units) ---

    fun hitOpening(p: Project, point: P, tolerance: Double): Selection? {
        var best: Selection? = null
        var bestD = Double.MAX_VALUE
        fun check(kind: Kind, o: Opening) {
            val wall = p.wall(o.wallId) ?: return
            val g = openingGeometry(wall, o) ?: return
            val d = projectPointOnSegment(point, g.start, g.end).distance
            if (d <= wall.thickness / 2 + tolerance && d < bestD) {
                bestD = d
                best = Selection(kind, o.id)
            }
        }
        p.doors.forEach { check(Kind.DOOR, it) }
        p.windows.forEach { check(Kind.WINDOW, it) }
        return best
    }

    fun hitWall(p: Project, point: P, tolerance: Double): Wall? =
        p.walls
            .map { it to projectPointOnSegment(point, it.a, it.b).distance }
            .filter { (w, d) -> d <= w.thickness / 2 + tolerance }
            .minByOrNull { it.second }?.first

    /** Topmost item under [point] (items drawn last win). */
    fun hitItem(p: Project, point: P, tolerance: Double = 0.0): Item? =
        p.items.lastOrNull { Furniture.contains(it, point, tolerance) }

    fun hitRoom(p: Project, point: P) =
        p.rooms.filter { pointInPolygon(point, it.polygon) }.minByOrNull { it.areaM2 }

    fun hit(p: Project, point: P, tolerance: Double): Selection? =
        hitOpening(p, point, tolerance)
            ?: hitItem(p, point)?.let { Selection(Kind.ITEM, it.id) }
            ?: hitWall(p, point, tolerance)?.let { Selection(Kind.WALL, it.id) }
            ?: hitRoom(p, point)?.let { Selection(Kind.ROOM, it.id) }
}
