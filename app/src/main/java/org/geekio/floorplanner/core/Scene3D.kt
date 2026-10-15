package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Opening
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A 3D point: plan x and y (y down, as on the plan) and height z. */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(dot(this))
    fun normalized(): V3 = length().let { if (it == 0.0) this else this * (1 / it) }
}

enum class FaceKind { FLOOR, RUG, WALL, WALL_TOP, GLASS, ITEM, ITEM_TOP }

/**
 * A flat polygon of the 3D model. [color] is ARGB. [outward] points out of the solid the face belongs to, so it
 * can be skipped when it faces away from the camera; null for faces seen from both sides (floors, glass).
 */
data class Face(val points: List<V3>, val color: Long, val kind: FaceKind, val outward: V3? = null)

/** A point on screen with its distance in front of the camera. */
data class Projected(val x: Double, val y: Double, val depth: Double)

/**
 * Orbit camera looking at [target] from [distance] away, turned [yaw] radians around the vertical axis and
 * raised [pitch] radians above the ground. [focal] is in screen pixels.
 */
data class Camera(val target: V3, val yaw: Double, val pitch: Double, val distance: Double, val focal: Double) {
    // The plan's y axis points down, so it is flipped to get a right-handed space.
    private fun rh(p: V3) = V3(p.x, -p.y, p.z)

    private val eye: V3 = rh(target) + V3(cos(pitch) * cos(yaw), cos(pitch) * sin(yaw), sin(pitch)) * distance
    private val forward = (rh(target) - eye).normalized()
    private val right = forward.cross(V3(0.0, 0.0, 1.0)).normalized()
    private val up = right.cross(forward)

    /** Whether a face at [p] pointing [outward] (plan coordinates) turns towards the camera. */
    fun faces(p: V3, outward: V3): Boolean = rh(outward).dot(eye - rh(p)) > 0

    /** Screen position relative to the centre of the view (y down), or null when behind the camera. */
    fun project(p: V3): Projected? {
        val v = rh(p) - eye
        val depth = v.dot(forward)
        if (depth < NEAR) return null
        return Projected(focal * v.dot(right) / depth, -focal * v.dot(up) / depth, depth)
    }

    /** 0..1 lighting of a face from its points (a soft light from above and the camera side). */
    fun shade(points: List<V3>): Double {
        if (points.size < 3) return 1.0
        val a = rh(points[0])
        var n = (rh(points[1]) - a).cross(rh(points[2]) - a).normalized()
        if (n.dot(eye - a) < 0) n = n * -1.0
        val light = V3(0.4, -0.5, 0.75).normalized()
        return 0.6 + 0.4 * maxOf(0.0, n.dot(light))
    }

    companion object {
        const val NEAR = 1.0
    }
}

/** Turns a plan into extruded walls (with door and window openings), room floors and furniture blocks. */
object Scene3D {
    private const val WALL = 0xFFEDEBE6
    private const val WALL_TOP = 0xFF5B6270
    private const val GLASS = 0x667EB6E8L
    private val categoryColor = mapOf(
        FurnitureCategory.LIVING to 0xFFB9A48C,
        FurnitureCategory.BEDROOM to 0xFF9DB4C0,
        FurnitureCategory.KITCHEN to 0xFFD3CEC2,
        FurnitureCategory.BATHROOM to 0xFFE6F0F4,
        FurnitureCategory.PLUMBING to 0xFFE6F0F4,
        FurnitureCategory.STRUCTURE to 0xFFD9D6CF,
    )

    /** Furniture height in cm, by catalog key. */
    fun itemHeight(type: String): Double = when (type) {
        "sofa3", "sofa2", "armchair" -> 80.0
        "coffee_table" -> 45.0
        "tv_stand" -> 50.0
        "bookshelf" -> 200.0
        "plant" -> 100.0
        "bed_double", "bed_single" -> 50.0
        "nightstand" -> 55.0
        "wardrobe" -> 210.0
        "fridge" -> 180.0
        "stove", "kitchen_sink", "counter", "basin" -> 90.0
        "washer" -> 85.0
        "toilet" -> 40.0
        "squat_toilet", "shower" -> 5.0
        "bathtub" -> 55.0
        "radiator" -> 60.0
        "water_heater" -> 150.0
        "spiral_stairs" -> 100.0
        "fireplace" -> 110.0
        "base_cabinet", "corner_cabinet", "oven", "kitchen_island", "vanity" -> 90.0
        "dishwasher", "dryer", "dresser" -> 85.0
        "tall_cabinet" -> 220.0
        "wall_cabinet" -> 70.0
        "range_hood" -> 45.0
        "bar_stool" -> 75.0
        "low_table" -> 30.0
        "side_table" -> 55.0
        "console_table" -> 80.0
        "ottoman" -> 42.0
        "dining_chair" -> 85.0
        "office_chair" -> 100.0
        "shoe_cabinet" -> 100.0
        "piano" -> 120.0
        "bunk_bed" -> 160.0
        "crib" -> 95.0
        "dressing_table" -> 75.0
        else -> 75.0
    }

    /** Height above the floor of the underside of hung items (wall cabinets, hoods). */
    fun itemBase(type: String): Double = when (type) {
        "wall_cabinet" -> 145.0
        "range_hood" -> 155.0
        else -> 0.0
    }

    /** All faces of the open floor. [height] overrides the wall height (to cut the walls low). */
    fun build(p: Project, height: Double = p.wallHeight): List<Face> {
        val faces = mutableListOf<Face>()
        p.rooms.forEachIndexed { i, room ->
            val poly = room.polygon
            if (poly.size < 3) return@forEachIndexed
            val c = FloorPatterns.parseColor(FloorPatterns.colorFor(room.color, i)) ?: 0xFFDDDDDD
            faces += Face(poly.map { V3(it.x, it.y, 0.0) }, blend(c, 0xFFFFFFFF, if (room.color != null) 0.25 else 0.6), FaceKind.FLOOR)
        }
        for (wall in p.walls) wallFaces(p, wall, minOf(wall.height ?: height, height), faces)
        for (item in p.items) {
            if (Furniture.isFixture(item) && item.type != "radiator" && item.type != "water_heater") continue
            when (item.type) {
                "stairs" -> stairFaces(item, p.wallHeight, faces)
                "column_square", "column_round" -> itemFaces(item, height, faces)
                "rug" -> faces += Face(Furniture.corners(item).map { V3(it.x, it.y, 0.0) }, 0xFFB48C78, FaceKind.RUG)
                else -> itemFaces(item, itemHeight(item.type), faces)
            }
        }
        return faces
    }

    private fun blend(a: Long, b: Long, t: Double): Long {
        fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toDouble()
        fun mix(s: Int) = (ch(a, s) * (1 - t) + ch(b, s) * t).toLong() shl s
        return (0xFFL shl 24) or mix(16) or mix(8) or mix(0)
    }

    private data class Span(val from: Double, val to: Double, val bottom: Double, val top: Double)

    private fun wallFaces(p: Project, wall: Wall, height: Double, out: MutableList<Face>) {
        val path = Walls.path(wall)
        val len = Walls.length(wall)
        if (len < 1) return
        val openings: List<Pair<Opening, Boolean>> =
            p.doors.filter { it.wallId == wall.id }.map { it to true } + p.windows.filter { it.wallId == wall.id }.map { it to false }
        // Solid pieces along the wall: full height between openings, lintels and sills at the openings.
        val cuts = openings.map { (o, door) ->
            val half = minOf(o.width, len) / 2
            Triple(o.offset.coerceIn(half, len - half) - half, o.offset.coerceIn(half, len - half) + half, door)
        }.sortedBy { it.first }
        val spans = mutableListOf<Span>()
        var at = 0.0
        for ((from, to, door) in cuts) {
            if (from > at) spans += Span(at, from, 0.0, height)
            val top = if (door) Estimates.DOOR_HEIGHT else Estimates.WINDOW_SILL + Estimates.WINDOW_HEIGHT
            if (height > top) spans += Span(from, to, top, height)
            if (!door) {
                spans += Span(from, to, 0.0, minOf(Estimates.WINDOW_SILL, height))
                if (height > Estimates.WINDOW_SILL) {
                    val g0 = Walls.pointAt(path, (from + to) / 2)
                    val a = Walls.pointAt(path, from)
                    val b = Walls.pointAt(path, to)
                    val gTop = minOf(top, height)
                    if (g0 != null && a != null && b != null) {
                        out += Face(listOf(V3(a.x, a.y, Estimates.WINDOW_SILL), V3(b.x, b.y, Estimates.WINDOW_SILL), V3(b.x, b.y, gTop), V3(a.x, a.y, gTop)), GLASS, FaceKind.GLASS)
                    }
                }
            }
            at = maxOf(at, to)
        }
        if (at < len) spans += Span(at, len, 0.0, height)
        val t = wall.thickness / 2
        for (s in spans) {
            if (s.to - s.from < 0.5 || s.top - s.bottom < 0.5) continue
            val pts = Walls.slice(path, s.from, s.to)
            if (pts.size < 2) continue
            // Left and right faces of the wall piece, following the (possibly curved) centre line.
            val left = mutableListOf<P>()
            val right = mutableListOf<P>()
            for (i in pts.indices) {
                val d = (pts[minOf(i + 1, pts.lastIndex)] - pts[maxOf(i - 1, 0)]).normalized().perp()
                left += pts[i] + d * t
                right += pts[i] - d * t
            }
            for (i in 0 until pts.lastIndex) {
                val mid = (pts[i] + pts[i + 1]) * 0.5
                out += quad(left[i], left[i + 1], s.bottom, s.top, WALL, (left[i] + left[i + 1]) * 0.5 - mid)
                out += quad(right[i], right[i + 1], s.bottom, s.top, WALL, (right[i] + right[i + 1]) * 0.5 - mid)
                out += Face(
                    listOf(V3(left[i].x, left[i].y, s.top), V3(left[i + 1].x, left[i + 1].y, s.top), V3(right[i + 1].x, right[i + 1].y, s.top), V3(right[i].x, right[i].y, s.top)),
                    WALL_TOP, FaceKind.WALL_TOP, UP,
                )
            }
            out += quad(left.first(), right.first(), s.bottom, s.top, WALL, pts[0] - pts[1])
            out += quad(left.last(), right.last(), s.bottom, s.top, WALL, pts.last() - pts[pts.lastIndex - 1])
        }
    }

    private val UP = V3(0.0, 0.0, 1.0)

    private fun quad(a: P, b: P, z0: Double, z1: Double, color: Long, outward: P) =
        Face(listOf(V3(a.x, a.y, z0), V3(b.x, b.y, z0), V3(b.x, b.y, z1), V3(a.x, a.y, z1)), color, FaceKind.WALL, V3(outward.x, outward.y, 0.0))

    /** Straight stairs as a run of steps rising towards the back of the item to the full storey height. */
    private fun stairFaces(item: org.geekio.floorplanner.model.Item, storey: Double, out: MutableList<Face>) {
        val n = maxOf(2, Math.round(item.depth / Furniture.STEP_DEPTH).toInt())
        val color = categoryColor.getValue(FurnitureCategory.STRUCTURE)
        for (i in 0 until n) {
            val y0 = item.depth / 2 - item.depth * (i + 1) / n
            val y1 = item.depth / 2 - item.depth * i / n
            val outline = listOf(P(-item.width / 2, y0), P(item.width / 2, y0), P(item.width / 2, y1), P(-item.width / 2, y1))
                .map { Furniture.toWorld(item, it) }
            prism(outline, 0.0, storey * (i + 1) / n, color, out)
        }
    }

    /** Upright solid over [outline] from [z0] to [z1]; a raised one also gets its underside. */
    private fun prism(outline: List<P>, z0: Double, z1: Double, color: Long, out: MutableList<Face>) {
        val c = P(outline.sumOf { it.x } / outline.size, outline.sumOf { it.y } / outline.size)
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[(i + 1) % outline.size]
            val o = (a + b) * 0.5 - c
            out += Face(listOf(V3(a.x, a.y, z0), V3(b.x, b.y, z0), V3(b.x, b.y, z1), V3(a.x, a.y, z1)), color, FaceKind.ITEM, V3(o.x, o.y, 0.0))
        }
        out += Face(outline.map { V3(it.x, it.y, z1) }, blend(color, 0xFFFFFFFF, 0.25), FaceKind.ITEM_TOP, UP)
        if (z0 > 0) out += Face(outline.map { V3(it.x, it.y, z0) }, color, FaceKind.ITEM, V3(0.0, 0.0, -1.0))
    }

    private fun itemFaces(item: org.geekio.floorplanner.model.Item, h: Double, out: MutableList<Face>) {
        val color = Furniture.type(item.type)?.category?.let { categoryColor[it] } ?: 0xFFB9A48C
        val solids = Furniture.shapes(item).filter {
            (it is Prim.Rect && it.fill) || (it is Prim.Circle && it.fill)
        }.ifEmpty { listOf(Prim.Rect(-item.width / 2, -item.depth / 2, item.width, item.depth, fill = true)) }
        for (prim in solids) {
            val outline = when (prim) {
                is Prim.Rect -> listOf(P(prim.x, prim.y), P(prim.x + prim.w, prim.y), P(prim.x + prim.w, prim.y + prim.h), P(prim.x, prim.y + prim.h))
                is Prim.Circle -> (0 until 12).map { k -> val a = Math.PI * 2 * k / 12; P(prim.cx + cos(a) * prim.r, prim.cy + sin(a) * prim.r) }
                is Prim.Line -> continue
            }.map { Furniture.toWorld(item, it) }
            val z0 = itemBase(item.type)
            prism(outline, z0, z0 + h, color, out)
        }
    }

    /**
     * Cuts four-sided faces into pieces at most [along] wide and [across] high, so that painting far to near by
     * each piece's average depth draws furniture over the wall behind it instead of a long wall's middle
     * deciding for its whole length. Other faces (floors, round tops) are kept whole.
     */
    fun split(faces: List<Face>, along: Double = 50.0, across: Double = 70.0): List<Face> = faces.flatMap { f ->
        if (f.points.size != 4 || f.kind == FaceKind.FLOOR || f.kind == FaceKind.RUG) return@flatMap listOf(f)
        val (p0, p1, p2, p3) = f.points
        val n = maxOf(1, ceil(maxOf(dist(p0, p1), dist(p3, p2)) / along).toInt())
        val m = maxOf(1, ceil(maxOf(dist(p0, p3), dist(p1, p2)) / across).toInt())
        if (n == 1 && m == 1) return@flatMap listOf(f)
        // Bilinear grid over the quad.
        fun at(u: Double, v: Double): V3 {
            val a = lerp(p0, p1, u)
            val b = lerp(p3, p2, u)
            return lerp(a, b, v)
        }
        (0 until n).flatMap { i ->
            (0 until m).map { j ->
                val u0 = i.toDouble() / n
                val u1 = (i + 1).toDouble() / n
                val v0 = j.toDouble() / m
                val v1 = (j + 1).toDouble() / m
                Face(listOf(at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1)), f.color, f.kind, f.outward)
            }
        }
    }

    private fun dist(a: V3, b: V3) = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

    private fun lerp(a: V3, b: V3, t: Double) = V3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t)

    /**
     * Faces in the order to paint them for [cam]: floors first, then rugs, then everything else far to near. Faces turned
     * away from the camera are left out, since the front of the same solid covers them.
     */
    fun paintOrder(faces: List<Face>, cam: Camera): List<Pair<Face, List<Projected>>> {
        val drawn = faces.mapNotNull { f ->
            if (f.outward != null && !cam.faces(f.points[0], f.outward)) return@mapNotNull null
            val pts = f.points.map { cam.project(it) ?: return@mapNotNull null }
            Triple(f, pts, pts.sumOf { it.depth } / pts.size)
        }
        val floors = drawn.filter { it.first.kind == FaceKind.FLOOR }
        val rugs = drawn.filter { it.first.kind == FaceKind.RUG }
        val rest = drawn.filter { it.first.kind != FaceKind.FLOOR && it.first.kind != FaceKind.RUG }
        return (floors.sortedByDescending { it.third } + rugs.sortedByDescending { it.third } + rest.sortedByDescending { it.third })
            .map { it.first to it.second }
    }

    /** Point the camera looks at and a distance that shows the whole plan. */
    fun frame(p: Project): Pair<V3, Double> {
        val pts = ProjectIO.extentPoints(p).ifEmpty { listOf(P(0.0, 0.0), P(500.0, 500.0)) }
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val size = maxOf(maxX - minX, maxY - minY, 300.0)
        return V3((minX + maxX) / 2, (minY + maxY) / 2, 0.0) to size * 1.4
    }
}
