package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import java.util.Locale
import kotlin.math.atan2

/**
 * AutoCAD R12 DXF export of the open floor, in centimetres with y pointing up. Walls are outlined with gaps
 * for doors and windows; everything else goes on its own layer.
 */
object Dxf {
    private val layers = listOf(
        "WALLS" to 7, "DOORS" to 1, "WINDOWS" to 5, "ROOMS" to 3, "FURNITURE" to 8,
        "ELECTRICAL" to 30, "PLUMBING" to 4, "DIMENSIONS" to 2, "TEXT" to 7,
    )

    private class Writer {
        val sb = StringBuilder()
        fun pair(code: Int, value: String) = sb.append(code).append('\n').append(value).append('\n')
        fun num(code: Int, v: Double) = pair(code, String.format(Locale.US, "%.2f", v))

        fun line(layer: String, a: P, b: P) {
            if (a.dist(b) < 0.01) return
            pair(0, "LINE"); pair(8, layer)
            num(10, a.x); num(20, -a.y); num(30, 0.0)
            num(11, b.x); num(21, -b.y); num(31, 0.0)
        }

        fun circle(layer: String, c: P, r: Double) {
            pair(0, "CIRCLE"); pair(8, layer)
            num(10, c.x); num(20, -c.y); num(30, 0.0); num(40, r)
        }

        /** An arc around [c] from [from] to [to] the short way round (in plan coordinates). */
        fun arc(layer: String, c: P, from: P, to: P) {
            // With y flipped, angles are measured counter-clockwise as DXF expects.
            val a1 = Math.toDegrees(atan2(-(from.y - c.y), from.x - c.x))
            val a2 = Math.toDegrees(atan2(-(to.y - c.y), to.x - c.x))
            var sweep = a2 - a1
            while (sweep < 0) sweep += 360.0
            val (start, end) = if (sweep <= 180) a1 to a2 else a2 to a1
            pair(0, "ARC"); pair(8, layer)
            num(10, c.x); num(20, -c.y); num(30, 0.0); num(40, c.dist(from))
            num(50, (start + 360) % 360); num(51, (end + 360) % 360)
        }

        fun text(layer: String, at: P, height: Double, s: String) {
            if (s.isBlank()) return
            pair(0, "TEXT"); pair(8, layer)
            num(10, at.x); num(20, -at.y); num(30, 0.0); num(40, height)
            pair(1, s.replace('\n', ' '))
            // Centred on the point.
            pair(72, "1"); pair(73, "2")
            num(11, at.x); num(21, -at.y); num(31, 0.0)
        }
    }

    fun export(p: Project, units: UnitSystem = UnitSystem.METRIC, showDimensions: Boolean = true): String {
        val w = Writer()
        w.pair(0, "SECTION"); w.pair(2, "HEADER")
        w.pair(9, "\$ACADVER"); w.pair(1, "AC1009")
        w.pair(9, "\$INSUNITS"); w.pair(70, "5")
        w.pair(0, "ENDSEC")
        w.pair(0, "SECTION"); w.pair(2, "TABLES")
        w.pair(0, "TABLE"); w.pair(2, "LAYER"); w.pair(70, layers.size.toString())
        for ((name, color) in layers) {
            w.pair(0, "LAYER"); w.pair(2, name); w.pair(70, "0"); w.pair(62, color.toString()); w.pair(6, "CONTINUOUS")
        }
        w.pair(0, "ENDTAB"); w.pair(0, "ENDSEC")

        w.pair(0, "SECTION"); w.pair(2, "ENTITIES")
        for (wall in p.walls) wallOutline(w, p, wall)
        for (door in p.doors) {
            val wall = p.wall(door.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, door) ?: continue
            for (s in PlanOps.doorLeaves(g, door)) {
                w.line("DOORS", s.hinge, s.leafEnd)
                w.arc("DOORS", s.hinge, s.strike, s.leafEnd)
            }
            if (door.style == Door.SLIDING) for ((a, b) in PlanOps.slidingPanels(g)) w.line("DOORS", a, b)
        }
        for (win in p.windows) {
            val wall = p.wall(win.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, win) ?: continue
            for (k in listOf(-0.3, 0.3)) {
                val o = g.normal * (wall.thickness / 2 * k)
                w.line("WINDOWS", g.start + o, g.end + o)
            }
        }
        val upm = p.scale.unitsPerMeter
        for (room in p.rooms) {
            val c = room.center ?: continue
            w.text("ROOMS", c - P(0.0, 12.0), 16.0, room.name)
            w.text("ROOMS", c + P(0.0, 12.0), 12.0, Units.area(room.areaM2, units, Locale.US))
        }
        for (item in p.items) {
            val layer = when (Layers.of(item)) {
                Layer.ELECTRICAL -> "ELECTRICAL"
                Layer.PLUMBING -> "PLUMBING"
                else -> "FURNITURE"
            }
            fun t(x: Double, y: Double) = Furniture.toWorld(item, P(x, y))
            for (prim in Furniture.shapes(item)) when (prim) {
                is Prim.Rect -> {
                    val c = listOf(t(prim.x, prim.y), t(prim.x + prim.w, prim.y), t(prim.x + prim.w, prim.y + prim.h), t(prim.x, prim.y + prim.h))
                    for (i in 0 until 4) w.line(layer, c[i], c[(i + 1) % 4])
                }
                is Prim.Circle -> w.circle(layer, t(prim.cx, prim.cy), prim.r)
                is Prim.Line -> w.line(layer, t(prim.x1, prim.y1), t(prim.x2, prim.y2))
            }
        }
        val dims = (if (showDimensions) Dimensions.overall(p, 50.0) else emptyList()) + p.dimensions.mapNotNull { Dimensions.geometry(it) }
        for (g in dims) {
            w.line("DIMENSIONS", g.fromA, g.start)
            w.line("DIMENSIONS", g.fromB, g.end)
            w.line("DIMENSIONS", g.start, g.end)
            val tick = ((g.end - g.start).normalized() + g.normal) * 6.0
            w.line("DIMENSIONS", g.start - tick, g.start + tick)
            w.line("DIMENSIONS", g.end - tick, g.end + tick)
            w.text("DIMENSIONS", g.labelAt(12.0), 12.0, Units.lengthShort(g.start.dist(g.end) / upm, units, Locale.US))
        }
        for (l in p.labels) {
            w.text("TEXT", l.anchor, l.size, l.text)
            l.tip?.let { tip ->
                w.line("TEXT", l.anchor, tip)
                val (h1, h2) = PlanOps.arrowHead(l.anchor, tip, l.size * 0.6)
                w.line("TEXT", tip, h1)
                w.line("TEXT", tip, h2)
            }
        }
        w.pair(0, "ENDSEC")
        w.pair(0, "EOF")
        return w.sb.toString()
    }

    /** Both faces of a wall, broken where its doors and windows are, with the jambs and end caps. */
    private fun wallOutline(w: Writer, p: Project, wall: Wall) {
        val path = Walls.path(wall)
        val len = Walls.length(path)
        if (len < 0.5) return
        val half = wall.thickness / 2
        val gaps = (p.doors.filter { it.wallId == wall.id } + p.windows.filter { it.wallId == wall.id })
            .mapNotNull { o -> PlanOps.openingGeometry(wall, o)?.let { g -> wall.a.dist(g.start) to wall.a.dist(g.end) } }
            .sortedBy { it.first }
        val solid = mutableListOf<Pair<Double, Double>>()
        var at = 0.0
        for ((from, to) in gaps) {
            if (from > at) solid += at to from
            at = maxOf(at, to)
        }
        if (at < len) solid += at to len
        for ((from, to) in solid) {
            val pts = Walls.slice(path, from, to)
            val sides = listOf(1.0, -1.0).map { side ->
                pts.indices.map { i ->
                    val d = (pts[minOf(i + 1, pts.lastIndex)] - pts[maxOf(i - 1, 0)]).normalized().perp()
                    pts[i] + d * (half * side)
                }
            }
            for (side in sides) for (i in 0 until side.lastIndex) w.line("WALLS", side[i], side[i + 1])
            w.line("WALLS", sides[0].first(), sides[1].first())
            w.line("WALLS", sides[0].last(), sides[1].last())
        }
    }
}
