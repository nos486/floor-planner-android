package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Floor
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.SavedVersion
import kotlinx.serialization.json.Json
import java.util.Locale

/** JSON (web-app compatible) and SVG export. */
object ProjectIO {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun toJson(p: Project): String = json.encodeToString(Project.serializer(), p)

    fun versionToJson(v: SavedVersion): String = json.encodeToString(SavedVersion.serializer(), v)

    fun versionFromJson(text: String): SavedVersion? = runCatching {
        val v = json.decodeFromString(SavedVersion.serializer(), text)
        v.copy(project = fromJson(toJson(v.project)) ?: return null)
    }.getOrNull()

    /** Parses a project file; returns null when it isn't a valid floor plan. */
    fun fromJson(text: String): Project? = try {
        val p = json.decodeFromString(Project.serializer(), text)
        val top = clean(Floor("", "", p.walls, p.doors, p.windows, p.rooms, p.items, p.dimensions, p.labels))
        PlanOps.withRooms(
            p.copy(
                scale = p.scale.copy(
                    unitsPerMeter = p.scale.unitsPerMeter.takeIf { it > 0 } ?: 100.0,
                    gridCm = p.scale.gridCm.takeIf { it > 0 } ?: 20.0,
                ),
                walls = top.walls,
                doors = top.doors,
                windows = top.windows,
                rooms = top.rooms,
                items = top.items,
                labels = top.labels,
                dimensions = top.dimensions,
                floors = p.floors.map(::clean),
                floorIndex = p.floorIndex.coerceIn(0, maxOf(0, p.floors.size - 1)),
            )
        )
    } catch (e: Exception) {
        null
    }

    /** Drops broken entries (walls without two points, doors on missing walls, and so on). */
    private fun clean(f: Floor): Floor {
        val walls = f.walls.filter { it.points.size >= 2 && it.points.all { pt -> pt.size >= 2 } }
        val wallIds = walls.map { it.id }.toSet()
        return f.copy(
            walls = walls,
            doors = f.doors.filter { it.wallId in wallIds },
            windows = f.windows.filter { it.wallId in wallIds },
            rooms = f.rooms.filter { it.centroid.size >= 2 },
            items = f.items.filter { it.width > 0 && it.depth > 0 },
            labels = f.labels.filter { it.text.isNotBlank() && it.size > 0 },
            dimensions = f.dimensions.filter { it.points.size >= 2 && it.points.all { pt -> pt.size >= 2 } },
        )
    }

    fun fileName(p: Project, ext: String): String {
        val base = p.projectName.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_')
        return "${base.ifEmpty { "floor_plan" }}.$ext"
    }


    private fun n(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun svgPrim(prim: Prim): String = when (prim) {
        is Prim.Rect -> {
            val fill = if (prim.fill) " fill=\"#eceff3\"" else ""
            """<rect x="${n(prim.x)}" y="${n(prim.y)}" width="${n(prim.w)}" height="${n(prim.h)}" rx="${n(prim.r)}"$fill/>"""
        }
        is Prim.Circle -> {
            val fill = if (prim.fill) " fill=\"#eceff3\"" else ""
            """<circle cx="${n(prim.cx)}" cy="${n(prim.cy)}" r="${n(prim.r)}"$fill/>"""
        }
        is Prim.Line -> """<line x1="${n(prim.x1)}" y1="${n(prim.y1)}" x2="${n(prim.x2)}" y2="${n(prim.y2)}" stroke-linecap="round"/>"""
    }

    private fun svgDimension(g: DimensionGeometry, upm: Double, u: UnitSystem): String {
        val c = "#374151"
        val dir = (g.end - g.start).normalized()
        val tick = (dir + g.normal) * 6.0
        val sb = StringBuilder("""<g stroke="$c" stroke-width="1">""")
        fun line(a: P, b: P) = sb.append("""<line x1="${n(a.x)}" y1="${n(a.y)}" x2="${n(b.x)}" y2="${n(b.y)}"/>""")
        val over = g.normal * (6.0 * g.side)
        line(g.fromA, g.start + over)
        line(g.fromB, g.end + over)
        line(g.start, g.end)
        line(g.start - tick, g.start + tick)
        line(g.end - tick, g.end + tick)
        val label = esc(Units.lengthShort(g.start.dist(g.end) / upm, u, Locale.US))
        val m = g.labelAt(9.0)
        sb.append("""<text x="${n(m.x)}" y="${n(m.y)}" font-size="12" fill="$c" stroke="none" text-anchor="middle" dominant-baseline="middle">$label</text>""")
        sb.append("</g>")
        return sb.toString()
    }

    /** Every point the plan covers (walls and furniture corners), used to frame views and exports. */
    fun extentPoints(p: Project): List<P> = p.walls.flatMap { Walls.path(it) } + p.items.flatMap { Furniture.corners(it) }

    fun toSvg(p: Project, units: UnitSystem = UnitSystem.METRIC, showDimensions: Boolean = true): String {
        val pts = extentPoints(p)
        val margin = 100.0
        val minX = (pts.minOfOrNull { it.x } ?: 0.0) - margin
        val minY = (pts.minOfOrNull { it.y } ?: 0.0) - margin
        val maxX = (pts.maxOfOrNull { it.x } ?: 500.0) + margin
        val maxY = (pts.maxOfOrNull { it.y } ?: 500.0) + margin
        val w = maxX - minX
        val h = maxY - minY
        val upm = p.scale.unitsPerMeter
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="${n(minX)} ${n(minY)} ${n(w)} ${n(h)}" width="${n(w)}" height="${n(h)}" font-family="sans-serif">""").append('\n')
        sb.append("""<rect x="${n(minX)}" y="${n(minY)}" width="${n(w)}" height="${n(h)}" fill="#ffffff"/>""").append('\n')

        p.rooms.forEachIndexed { i, r ->
            val poly = r.polygon.joinToString(" ") { "${n(it.x)},${n(it.y)}" }
            val opacity = if (r.color != null) "0.6" else "0.15"
            sb.append("""<polygon points="$poly" fill="${FloorPatterns.colorFor(r.color, i)}" fill-opacity="$opacity"/>""").append('\n')
            if (r.floor != null && r.polygon.size >= 3) {
                val min = P(r.polygon.minOf { it.x }, r.polygon.minOf { it.y })
                val max = P(r.polygon.maxOf { it.x }, r.polygon.maxOf { it.y })
                sb.append("""<clipPath id="clip$i"><polygon points="$poly"/></clipPath>""").append('\n')
                sb.append("""<g clip-path="url(#clip$i)" stroke="#2b2f36" stroke-opacity="0.22" stroke-width="1">""")
                for ((a, b) in FloorPatterns.lines(r.floor, min, max)) {
                    sb.append("""<line x1="${n(a.x)}" y1="${n(a.y)}" x2="${n(b.x)}" y2="${n(b.y)}"/>""")
                }
                sb.append("</g>\n")
            }
        }
        for (item in p.items.sortedBy { Furniture.layer(it) }) {
            val stroke = when (Furniture.type(item.type)?.category) {
                FurnitureCategory.ELECTRICAL -> "#d97706"
                FurnitureCategory.PLUMBING -> "#0284c7"
                else -> "#4b5563"
            }
            sb.append("""<g transform="translate(${n(item.x)} ${n(item.y)}) rotate(${n(item.rotation)})" stroke="$stroke" stroke-width="1.5" fill="none">""").append('\n')
            for (prim in Furniture.shapes(item)) sb.append(svgPrim(prim)).append('\n')
            sb.append("</g>\n")
        }
        for (wall in p.walls) {
            val opacity = if (wall.height != null) "0.45" else "1"
            val pts = Walls.path(wall).joinToString(" ") { "${n(it.x)},${n(it.y)}" }
            sb.append("""<polyline points="$pts" fill="none" stroke="#2b2f36" stroke-opacity="$opacity" stroke-width="${n(wall.thickness)}" stroke-linecap="round" stroke-linejoin="round"/>""").append('\n')
        }
        for (door in p.doors) {
            val wall = p.wall(door.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, door) ?: continue
            sb.append("""<line x1="${n(g.start.x)}" y1="${n(g.start.y)}" x2="${n(g.end.x)}" y2="${n(g.end.y)}" stroke="#ffffff" stroke-width="${n(wall.thickness + 2)}"/>""").append('\n')
            for (s in PlanOps.doorLeaves(g, door)) {
                sb.append("""<line x1="${n(s.hinge.x)}" y1="${n(s.hinge.y)}" x2="${n(s.leafEnd.x)}" y2="${n(s.leafEnd.y)}" stroke="#2b2f36" stroke-width="3"/>""").append('\n')
                val sweep = if (s.clockwise) 1 else 0
                sb.append("""<path d="M ${n(s.strike.x)} ${n(s.strike.y)} A ${n(s.radius)} ${n(s.radius)} 0 0 $sweep ${n(s.leafEnd.x)} ${n(s.leafEnd.y)}" fill="none" stroke="#2b2f36" stroke-width="1.5" stroke-dasharray="6 4"/>""").append('\n')
            }
            if (door.style == Door.SLIDING) {
                for ((a, b) in PlanOps.slidingPanels(g)) {
                    sb.append("""<line x1="${n(a.x)}" y1="${n(a.y)}" x2="${n(b.x)}" y2="${n(b.y)}" stroke="#2b2f36" stroke-width="3"/>""").append('\n')
                }
            }
        }
        for (win in p.windows) {
            val wall = p.wall(win.wallId) ?: continue
            val g = PlanOps.openingGeometry(wall, win) ?: continue
            sb.append("""<line x1="${n(g.start.x)}" y1="${n(g.start.y)}" x2="${n(g.end.x)}" y2="${n(g.end.y)}" stroke="#ffffff" stroke-width="${n(wall.thickness + 2)}"/>""").append('\n')
            for (k in listOf(-0.3, 0.3)) {
                val o = g.normal * (wall.thickness / 2 * k)
                sb.append("""<line x1="${n(g.start.x + o.x)}" y1="${n(g.start.y + o.y)}" x2="${n(g.end.x + o.x)}" y2="${n(g.end.y + o.y)}" stroke="#2b7bd6" stroke-width="2"/>""").append('\n')
            }
        }
        for (wall in p.walls) {
            if (wall.length < 40 || !showDimensions) continue
            val mid = Walls.apex(wall)
            val off = mid + (wall.b - wall.a).normalized().perp() * (wall.thickness / 2 + 14)
            val label = esc(Units.length(Walls.length(wall) / upm, units, Locale.US))
            sb.append("""<text x="${n(off.x)}" y="${n(off.y)}" font-size="12" fill="#6b7280" text-anchor="middle" dominant-baseline="middle">$label</text>""").append('\n')
        }
        for (g in (if (showDimensions) Dimensions.overall(p, 50.0) else emptyList()) + p.dimensions.mapNotNull { Dimensions.geometry(it) }) {
            sb.append(svgDimension(g, upm, units)).append('\n')
        }
        for (l in p.labels) {
            l.tip?.let { tip ->
                val (h1, h2) = PlanOps.arrowHead(l.anchor, tip, l.size * 0.6)
                sb.append("""<line x1="${n(l.x)}" y1="${n(l.y)}" x2="${n(tip.x)}" y2="${n(tip.y)}" stroke="#1f2937" stroke-width="${n(l.size * 0.08)}"/>""")
                sb.append("""<polygon points="${n(tip.x)},${n(tip.y)} ${n(h1.x)},${n(h1.y)} ${n(h2.x)},${n(h2.y)}" fill="#1f2937"/>""").append('\n')
            }
            sb.append("""<text x="${n(l.x)}" y="${n(l.y)}" font-size="${n(l.size)}" fill="#1f2937" stroke="#ffffff" stroke-width="${n(l.size * 0.15)}" paint-order="stroke" text-anchor="middle" dominant-baseline="middle">${esc(l.text)}</text>""").append('\n')
        }
        for (r in p.rooms) {
            val c = r.center ?: continue
            sb.append("""<text x="${n(c.x)}" y="${n(c.y - 8)}" font-size="16" font-weight="bold" fill="#1f2937" text-anchor="middle">${esc(r.name)}</text>""").append('\n')
            sb.append("""<text x="${n(c.x)}" y="${n(c.y + 12)}" font-size="13" fill="#4b5563" text-anchor="middle">${Units.area(r.areaM2, units, Locale.US)}</text>""").append('\n')
        }
        sb.append("</svg>\n")
        return sb.toString()
    }
}
