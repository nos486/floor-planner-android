package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project

/**
 * Operations on several selected things at once: walls (with their doors and windows), furniture,
 * labels and measurements. Rooms follow their walls and are not selected themselves.
 */
object GroupOps {
    private val groupKinds = setOf(Kind.WALL, Kind.ITEM, Kind.LABEL, Kind.DIMENSION)

    /** Normalizes a hit for group selection: a door or window stands for its wall; rooms can't be grouped. */
    fun member(p: Project, sel: Selection): Selection? = when (sel.kind) {
        Kind.DOOR -> p.door(sel.id)?.let { Selection(Kind.WALL, it.wallId) }
        Kind.WINDOW -> p.window(sel.id)?.let { Selection(Kind.WALL, it.wallId) }
        Kind.ROOM -> null
        else -> sel.takeIf { it.kind in groupKinds }
    }

    /** Everything that lies completely inside the box spanned by [a] and [b]. */
    fun inBox(p: Project, a: P, b: P): Set<Selection> {
        val minX = minOf(a.x, b.x)
        val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y)
        val maxY = maxOf(a.y, b.y)
        fun inside(pt: P) = pt.x in minX..maxX && pt.y in minY..maxY
        val out = mutableSetOf<Selection>()
        p.walls.filter { inside(it.a) && inside(it.b) }.forEach { out += Selection(Kind.WALL, it.id) }
        p.items.filter { Furniture.corners(it).all(::inside) }.forEach { out += Selection(Kind.ITEM, it.id) }
        p.labels.filter { inside(it.anchor) && (it.tip?.let(::inside) ?: true) }.forEach { out += Selection(Kind.LABEL, it.id) }
        p.dimensions.filter { inside(it.a) && inside(it.b) }.forEach { out += Selection(Kind.DIMENSION, it.id) }
        return out
    }

    /** Drops members that no longer exist. */
    fun existing(p: Project, group: Set<Selection>): Set<Selection> = group.filter { s ->
        when (s.kind) {
            Kind.WALL -> p.wall(s.id) != null
            Kind.ITEM -> p.item(s.id) != null
            Kind.LABEL -> p.label(s.id) != null
            Kind.DIMENSION -> p.dimension(s.id) != null
            else -> false
        }
    }.toSet()

    private fun ids(group: Set<Selection>, kind: Kind) = group.filter { it.kind == kind }.map { it.id }.toSet()

    /** Points of every member, for the group's centre. */
    fun points(p: Project, group: Set<Selection>): List<P> {
        val walls = ids(group, Kind.WALL)
        val items = ids(group, Kind.ITEM)
        val labels = ids(group, Kind.LABEL)
        val dims = ids(group, Kind.DIMENSION)
        return p.walls.filter { it.id in walls }.flatMap { listOf(it.a, it.b) } +
            p.items.filter { it.id in items }.flatMap { Furniture.corners(it) } +
            p.labels.filter { it.id in labels }.flatMap { listOfNotNull(it.anchor, it.tip) } +
            p.dimensions.filter { it.id in dims }.flatMap { listOf(it.a, it.b) }
    }

    fun center(p: Project, group: Set<Selection>): P? {
        val pts = points(p, group)
        if (pts.isEmpty()) return null
        return P((pts.minOf { it.x } + pts.maxOf { it.x }) / 2, (pts.minOf { it.y } + pts.maxOf { it.y }) / 2)
    }

    /**
     * Applies the point mapping [f] to every member. [turn] is added to furniture rotation, or with [mirror]
     * the furniture rotation is negated, door swings flip and measurement offsets change side.
     */
    private fun transform(p: Project, group: Set<Selection>, f: (P) -> P, turn: Double = 0.0, mirror: Boolean = false): Project {
        val walls = ids(group, Kind.WALL)
        val items = ids(group, Kind.ITEM)
        val labels = ids(group, Kind.LABEL)
        val dims = ids(group, Kind.DIMENSION)
        val moved = p.copy(
            walls = p.walls.map {
                if (it.id !in walls) it
                else it.withPoints(f(it.a), f(it.b)).let { w -> if (mirror) w.copy(bulge = -w.bulge) else w }
            },
            doors = if (mirror) p.doors.map { if (it.wallId in walls) it.copy(side = -it.side) else it } else p.doors,
            items = p.items.map {
                if (it.id !in items) it else {
                    val c = f(P(it.x, it.y)).rounded()
                    val rot = if (mirror) -it.rotation else it.rotation + turn
                    it.copy(x = c.x, y = c.y, rotation = Furniture.normalizeAngle(rot))
                }
            },
            labels = p.labels.map {
                if (it.id !in labels) it else {
                    val a = f(it.anchor).rounded()
                    it.copy(x = a.x, y = a.y, arrow = it.tip?.let { t -> f(t).rounded().toList() })
                }
            },
            dimensions = p.dimensions.map {
                if (it.id !in dims) it
                else it.copy(points = listOf(f(it.a).rounded().toList(), f(it.b).rounded().toList()), offset = if (mirror) -it.offset else it.offset)
            },
        )
        return if (walls.isEmpty()) moved else PlanOps.withRooms(moved)
    }

    fun translate(p: Project, group: Set<Selection>, delta: P): Project = transform(p, group, { it + delta })

    /** Turns the group 90° clockwise (on screen) around its centre. */
    fun rotate90(p: Project, group: Set<Selection>): Project {
        val c = center(p, group) ?: return p
        return transform(p, group, { P(c.x - (it.y - c.y), c.y + (it.x - c.x)) }, turn = 90.0)
    }

    /** Mirrors the group left-to-right around its centre. */
    fun mirror(p: Project, group: Set<Selection>): Project {
        val c = center(p, group) ?: return p
        return transform(p, group, { P(2 * c.x - it.x, it.y) }, mirror = true)
    }

    fun delete(p: Project, group: Set<Selection>): Project {
        val walls = ids(group, Kind.WALL)
        val out = p.copy(
            walls = p.walls.filter { it.id !in walls },
            doors = p.doors.filter { it.wallId !in walls },
            windows = p.windows.filter { it.wallId !in walls },
            items = p.items.filter { it.id !in ids(group, Kind.ITEM) },
            labels = p.labels.filter { it.id !in ids(group, Kind.LABEL) },
            dimensions = p.dimensions.filter { it.id !in ids(group, Kind.DIMENSION) },
        )
        return if (walls.isEmpty()) out else PlanOps.withRooms(out)
    }

    /** Copies the group, shifted by [shift]; returns the new project and the selection of the copies. */
    fun duplicate(p: Project, group: Set<Selection>, shift: P): Pair<Project, Set<Selection>> {
        val wallIds = ids(group, Kind.WALL)
        val newWallId = wallIds.associateWith { generateId("w") }
        val walls = p.walls.filter { it.id in wallIds }.map { it.copy(id = newWallId.getValue(it.id)).withPoints(it.a + shift, it.b + shift) }
        val doors = p.doors.filter { it.wallId in wallIds }.map { it.copy(id = generateId("d"), wallId = newWallId.getValue(it.wallId)) }
        val windows = p.windows.filter { it.wallId in wallIds }.map { it.copy(id = generateId("win"), wallId = newWallId.getValue(it.wallId)) }
        val items = p.items.filter { it.id in ids(group, Kind.ITEM) }.map {
            it.copy(id = generateId("it"), x = roundTo(it.x + shift.x, 1), y = roundTo(it.y + shift.y, 1))
        }
        val labels = p.labels.filter { it.id in ids(group, Kind.LABEL) }.map {
            val a = (it.anchor + shift).rounded()
            it.copy(id = generateId("lbl"), x = a.x, y = a.y, arrow = it.tip?.let { t -> (t + shift).rounded().toList() })
        }
        val dims = p.dimensions.filter { it.id in ids(group, Kind.DIMENSION) }.map {
            it.copy(id = generateId("dim"), points = listOf((it.a + shift).rounded().toList(), (it.b + shift).rounded().toList()))
        }
        var out = p.copy(
            walls = p.walls + walls, doors = p.doors + doors, windows = p.windows + windows,
            items = p.items + items, labels = p.labels + labels, dimensions = p.dimensions + dims,
        )
        if (walls.isNotEmpty()) out = PlanOps.withRooms(out)
        val sel = walls.map { Selection(Kind.WALL, it.id) } + items.map { Selection(Kind.ITEM, it.id) } +
            labels.map { Selection(Kind.LABEL, it.id) } + dims.map { Selection(Kind.DIMENSION, it.id) }
        return out to sel.toSet()
    }
}
