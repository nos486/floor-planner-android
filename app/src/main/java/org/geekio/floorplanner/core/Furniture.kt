package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Wall
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class FurnitureCategory {
    LIVING, BEDROOM, KITCHEN, BATHROOM, STRUCTURE, ELECTRICAL, PLUMBING;

    /** Electrical and plumbing symbols: small, drawn on top and in their own colours. */
    val isFixture get() = this == ELECTRICAL || this == PLUMBING
}

/** A kind of furniture in the catalog; sizes are the default footprint in cm. */
data class FurnitureType(val key: String, val category: FurnitureCategory, val width: Double, val depth: Double)

/**
 * Drawing primitive of a furniture symbol in the item's local frame: origin at the item's centre,
 * x along its width, y along its depth with the back (the side that goes against a wall) at -depth/2.
 * [fill] shapes are painted with the item colour, the others are outlines only.
 */
sealed interface Prim {
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double, val r: Double = 0.0, val fill: Boolean = false) : Prim
    data class Circle(val cx: Double, val cy: Double, val r: Double, val fill: Boolean = false) : Prim
    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : Prim
}

object Furniture {
    val catalog: List<FurnitureType> = listOf(
        FurnitureType("sofa3", FurnitureCategory.LIVING, 210.0, 90.0),
        FurnitureType("sofa2", FurnitureCategory.LIVING, 160.0, 85.0),
        FurnitureType("armchair", FurnitureCategory.LIVING, 85.0, 85.0),
        FurnitureType("coffee_table", FurnitureCategory.LIVING, 110.0, 60.0),
        FurnitureType("tv_stand", FurnitureCategory.LIVING, 160.0, 45.0),
        FurnitureType("dining4", FurnitureCategory.LIVING, 140.0, 150.0),
        FurnitureType("dining6", FurnitureCategory.LIVING, 190.0, 160.0),
        FurnitureType("round_table", FurnitureCategory.LIVING, 140.0, 140.0),
        FurnitureType("bookshelf", FurnitureCategory.LIVING, 100.0, 35.0),
        FurnitureType("plant", FurnitureCategory.LIVING, 50.0, 50.0),
        FurnitureType("bed_double", FurnitureCategory.BEDROOM, 160.0, 200.0),
        FurnitureType("bed_single", FurnitureCategory.BEDROOM, 90.0, 200.0),
        FurnitureType("nightstand", FurnitureCategory.BEDROOM, 50.0, 40.0),
        FurnitureType("wardrobe", FurnitureCategory.BEDROOM, 180.0, 60.0),
        FurnitureType("desk", FurnitureCategory.BEDROOM, 120.0, 90.0),
        FurnitureType("fridge", FurnitureCategory.KITCHEN, 70.0, 70.0),
        FurnitureType("stove", FurnitureCategory.KITCHEN, 60.0, 60.0),
        FurnitureType("kitchen_sink", FurnitureCategory.KITCHEN, 80.0, 60.0),
        FurnitureType("counter", FurnitureCategory.KITCHEN, 60.0, 60.0),
        FurnitureType("washer", FurnitureCategory.KITCHEN, 60.0, 60.0),
        FurnitureType("toilet", FurnitureCategory.BATHROOM, 40.0, 65.0),
        FurnitureType("squat_toilet", FurnitureCategory.BATHROOM, 45.0, 60.0),
        FurnitureType("bathtub", FurnitureCategory.BATHROOM, 170.0, 75.0),
        FurnitureType("shower", FurnitureCategory.BATHROOM, 90.0, 90.0),
        FurnitureType("basin", FurnitureCategory.BATHROOM, 55.0, 45.0),
        FurnitureType("column_square", FurnitureCategory.STRUCTURE, 30.0, 30.0),
        FurnitureType("column_round", FurnitureCategory.STRUCTURE, 30.0, 30.0),
        FurnitureType("stairs", FurnitureCategory.STRUCTURE, 100.0, 300.0),
        FurnitureType("spiral_stairs", FurnitureCategory.STRUCTURE, 160.0, 160.0),
        FurnitureType("fireplace", FurnitureCategory.STRUCTURE, 120.0, 50.0),
        FurnitureType("socket", FurnitureCategory.ELECTRICAL, 20.0, 12.0),
        FurnitureType("switch", FurnitureCategory.ELECTRICAL, 16.0, 12.0),
        FurnitureType("ceiling_light", FurnitureCategory.ELECTRICAL, 40.0, 40.0),
        FurnitureType("wall_light", FurnitureCategory.ELECTRICAL, 30.0, 15.0),
        FurnitureType("power_panel", FurnitureCategory.ELECTRICAL, 40.0, 15.0),
        FurnitureType("ac_unit", FurnitureCategory.ELECTRICAL, 90.0, 25.0),
        FurnitureType("water_point", FurnitureCategory.PLUMBING, 16.0, 16.0),
        FurnitureType("drain", FurnitureCategory.PLUMBING, 20.0, 20.0),
        FurnitureType("gas_valve", FurnitureCategory.PLUMBING, 20.0, 12.0),
        FurnitureType("water_heater", FurnitureCategory.PLUMBING, 55.0, 55.0),
        FurnitureType("boiler", FurnitureCategory.PLUMBING, 45.0, 35.0),
        FurnitureType("radiator", FurnitureCategory.PLUMBING, 100.0, 12.0),
    )

    /** Depth of one stair tread in cm. */
    const val STEP_DEPTH = 28.0

    fun isFixture(item: Item) = type(item.type)?.category?.isFixture == true

    fun type(key: String): FurnitureType? = catalog.firstOrNull { it.key == key }

    fun create(type: FurnitureType, center: P, rotation: Double = 0.0) =
        Item(generateId("it"), type.key, roundTo(center.x, 1), roundTo(center.y, 1), type.width, type.depth, rotation)

    /** Local point of [item] in world coordinates. */
    fun toWorld(item: Item, local: P): P {
        val r = Math.toRadians(item.rotation)
        val c = cos(r)
        val s = sin(r)
        return P(item.x + local.x * c - local.y * s, item.y + local.x * s + local.y * c)
    }

    /** World point in the local frame of [item]. */
    fun toLocal(item: Item, world: P): P {
        val r = Math.toRadians(item.rotation)
        val c = cos(r)
        val s = sin(r)
        val dx = world.x - item.x
        val dy = world.y - item.y
        return P(dx * c + dy * s, -dx * s + dy * c)
    }

    /** Footprint corners in world coordinates (back-left, back-right, front-right, front-left). */
    fun corners(item: Item): List<P> {
        val hw = item.width / 2
        val hd = item.depth / 2
        return listOf(P(-hw, -hd), P(hw, -hd), P(hw, hd), P(-hw, hd)).map { toWorld(item, it) }
    }

    fun contains(item: Item, world: P, tolerance: Double = 0.0): Boolean {
        val l = toLocal(item, world)
        return abs(l.x) <= item.width / 2 + tolerance && abs(l.y) <= item.depth / 2 + tolerance
    }

    fun normalizeAngle(deg: Double): Double {
        var a = deg % 360
        if (a < 0) a += 360
        return roundTo(a, 1).let { if (it >= 360) 0.0 else it }
    }

    /**
     * Places [item] with its back against the nearest wall when its centre ([center]) is close enough,
     * turning it to face away from the wall. Returns null when no wall is near.
     */
    fun snapToWall(item: Item, center: P, walls: List<Wall>, reach: Double): Item? {
        var best: Item? = null
        var bestD = Double.MAX_VALUE
        for (wall in walls) {
            if (wall.length < 1) continue
            val proj = projectPointOnSegment(center, wall.a, wall.b)
            val gap = proj.distance - wall.thickness / 2 - item.depth / 2
            if (abs(gap) > reach || proj.distance >= bestD) continue
            val dir = (wall.b - wall.a).normalized()
            var n = dir.perp()
            if ((center - proj.point).dot(n) < 0) n = -n
            // Keep the item inside the wall's length.
            val len = wall.length
            val half = item.width / 2
            val along = if (len >= item.width) (proj.t * len).coerceIn(half, len - half) else len / 2
            val pos = wall.a + dir * along + n * (wall.thickness / 2 + item.depth / 2)
            val rotation = Math.toDegrees(atan2(-n.x, n.y))
            bestD = proj.distance
            best = item.copy(x = roundTo(pos.x, 1), y = roundTo(pos.y, 1), rotation = normalizeAngle(rotation))
        }
        return best
    }

    /** The symbol of [item] in its local frame. */
    fun shapes(item: Item): List<Prim> {
        val w = item.width
        val d = item.depth
        val l = -w / 2
        val t = -d / 2
        val r = w / 2
        val b = d / 2
        fun rect(x: Double, y: Double, rw: Double, rh: Double, rad: Double = 0.0, fill: Boolean = false) =
            Prim.Rect(x, y, rw, rh, rad, fill)
        val body = rect(l, t, w, d, minOf(w, d) * 0.06, fill = true)
        return when (item.type) {
            "sofa3", "sofa2", "armchair" -> {
                val arm = minOf(w * 0.14, 22.0)
                val back = d * 0.25
                val seats = when (item.type) { "sofa3" -> 3; "sofa2" -> 2; else -> 1 }
                val seatW = (w - 2 * arm) / seats
                listOf(
                    body,
                    rect(l, t, w, back, 4.0),
                    rect(l, t, arm, d, 4.0),
                    rect(r - arm, t, arm, d, 4.0),
                ) + (1 until seats).map { i -> Prim.Line(l + arm + seatW * i, t + back, l + arm + seatW * i, b) }
            }
            "coffee_table" -> listOf(body, rect(l + 6, t + 6, w - 12, d - 12, 3.0))
            "tv_stand" -> listOf(body, rect(l + w * 0.15, t + 2, w * 0.7, 6.0, 1.0, fill = true))
            "dining4", "dining6", "round_table" -> diningSet(item)
            "bookshelf" -> listOf(body) + (1..3).map { i -> Prim.Line(l + w * i / 4, t, l + w * i / 4, b) }
            "plant" -> listOf(
                Prim.Circle(0.0, 0.0, minOf(w, d) / 2, fill = true),
                Prim.Circle(0.0, 0.0, minOf(w, d) / 4),
                Prim.Line(l * 0.7, 0.0, r * 0.7, 0.0),
                Prim.Line(0.0, t * 0.7, 0.0, b * 0.7),
            )
            "bed_double", "bed_single" -> {
                val pillowH = minOf(d * 0.15, 30.0)
                val pillows = if (item.type == "bed_double") 2 else 1
                val gap = 8.0
                val pw = (w - gap * (pillows + 1)) / pillows
                listOf(body, rect(l, t, w, 6.0, fill = true)) +
                    (0 until pillows).map { i -> rect(l + gap + i * (pw + gap), t + 12, pw, pillowH, 6.0) } +
                    listOf(Prim.Line(l, t + d * 0.35, r, t + d * 0.35), Prim.Line(l, t + d * 0.35, r, t + d * 0.45))
            }
            "nightstand" -> listOf(body, Prim.Circle(0.0, 0.0, minOf(w, d) * 0.25))
            "wardrobe" -> listOf(body, Prim.Line(0.0, t, 0.0, b), Prim.Line(l, t, r, b), Prim.Line(l, b, r, t))
            "desk" -> {
                val top = d * 0.6
                val chair = minOf(w * 0.4, 50.0)
                listOf(
                    rect(l, t, w, top, 2.0, fill = true),
                    rect(-chair / 2, t + top + 4, chair, minOf(chair, d - top - 4), 8.0, fill = true),
                )
            }
            "fridge" -> listOf(body, Prim.Line(l, t + 8, r, t + 8), Prim.Line(r - 8, t + 14, r - 8, b - 6))
            "stove" -> {
                val rr = minOf(w, d) * 0.16
                listOf(body) + listOf(-1.0 to -1.0, 1.0 to -1.0, -1.0 to 1.0, 1.0 to 1.0).map { (sx, sy) ->
                    Prim.Circle(sx * w / 4, sy * d / 4, rr)
                }
            }
            "kitchen_sink" -> listOf(body, rect(l + 8, t + 8, w - 16, d - 16, 6.0), Prim.Circle(0.0, t + 5, 2.5, fill = true))
            "counter" -> listOf(body, Prim.Line(l, t + 4, r, t + 4))
            "washer" -> listOf(body, Prim.Circle(0.0, 4.0, minOf(w, d) * 0.32), Prim.Line(l, t + 10, r, t + 10))
            "toilet" -> listOf(
                rect(l, t, w, d * 0.3, 4.0, fill = true),
                rect(l + w * 0.1, t + d * 0.3, w * 0.8, d * 0.7, w * 0.4, fill = true),
                rect(l + w * 0.22, t + d * 0.42, w * 0.56, d * 0.48, w * 0.28),
            )
            "squat_toilet" -> listOf(
                body,
                rect(l + w * 0.2, t + d * 0.15, w * 0.6, d * 0.7, w * 0.3),
                Prim.Circle(0.0, t + d * 0.3, minOf(w, d) * 0.08),
            )
            "bathtub" -> listOf(body, rect(l + 8, t + 8, w - 16, d - 16, minOf(w, d) * 0.3), Prim.Circle(l + 20, 0.0, 3.0, fill = true))
            "shower" -> listOf(body, Prim.Line(l, t, r, b), Prim.Line(l, b, r, t), Prim.Circle(0.0, 0.0, 4.0, fill = true))
            "basin" -> listOf(body, rect(l + w * 0.15, t + d * 0.25, w * 0.7, d * 0.6, minOf(w, d) * 0.3), Prim.Circle(0.0, t + d * 0.12, 2.5, fill = true))
            "column_square" -> listOf(body, Prim.Line(l, t, r, b), Prim.Line(l, b, r, t))
            "column_round" -> listOf(Prim.Circle(0.0, 0.0, minOf(w, d) / 2, fill = true), Prim.Line(-minOf(w, d) * 0.35, -minOf(w, d) * 0.35, minOf(w, d) * 0.35, minOf(w, d) * 0.35))
            "stairs" -> {
                // Treads every ~28 cm, with an arrow pointing up the stairs (towards the back).
                val n = maxOf(2, Math.round(d / STEP_DEPTH).toInt())
                listOf(body) + (1 until n).map { i -> Prim.Line(l, t + d * i / n, r, t + d * i / n) } + listOf(
                    Prim.Line(0.0, b - d * 0.08, 0.0, t + d * 0.06),
                    Prim.Line(0.0, t + d * 0.06, -w * 0.12, t + d * 0.06 + w * 0.15),
                    Prim.Line(0.0, t + d * 0.06, w * 0.12, t + d * 0.06 + w * 0.15),
                )
            }
            "spiral_stairs" -> {
                val rr = minOf(w, d) / 2
                listOf(Prim.Circle(0.0, 0.0, rr, fill = true), Prim.Circle(0.0, 0.0, rr * 0.12, fill = true)) +
                    (0 until 12).map { i -> val a = Math.PI * 2 * i / 12; Prim.Line(cos(a) * rr * 0.12, sin(a) * rr * 0.12, cos(a) * rr, sin(a) * rr) }
            }
            "fireplace" -> listOf(body, rect(l + w * 0.2, t + d * 0.25, w * 0.6, d * 0.75, 2.0), Prim.Line(l + w * 0.3, b, l + w * 0.5, t + d * 0.45), Prim.Line(l + w * 0.5, t + d * 0.45, l + w * 0.7, b))
            "socket" -> {
                val rr = minOf(w / 2, d)
                listOf(Prim.Circle(0.0, t, rr, fill = true), Prim.Line(-rr * 0.35, t + rr * 0.2, -rr * 0.35, t + rr * 0.6), Prim.Line(rr * 0.35, t + rr * 0.2, rr * 0.35, t + rr * 0.6))
            }
            "switch" -> {
                val rr = minOf(w, d) * 0.35
                listOf(Prim.Circle(0.0, 0.0, rr, fill = true), Prim.Line(0.0, 0.0, r, b), Prim.Line(r, b, r - rr * 0.8, b))
            }
            "ceiling_light" -> {
                val rr = minOf(w, d) / 2
                val k = rr * 0.707
                listOf(Prim.Circle(0.0, 0.0, rr, fill = true), Prim.Line(-k, -k, k, k), Prim.Line(-k, k, k, -k))
            }
            "wall_light" -> listOf(rect(l, t, w, d * 0.4, fill = true), Prim.Circle(0.0, t + d * 0.4, minOf(w * 0.3, d * 0.6), fill = true))
            "power_panel" -> listOf(body, Prim.Line(l, b, r, t), rect(l + w * 0.3, t, w * 0.4, d, fill = false))
            "ac_unit" -> listOf(body) + (1..3).map { i -> Prim.Line(l + 6, t + d * i / 4, r - 6, t + d * i / 4) }
            "water_point" -> {
                val rr = minOf(w, d) / 2
                listOf(Prim.Circle(0.0, 0.0, rr, fill = true), Prim.Line(-rr, 0.0, rr, 0.0), Prim.Line(0.0, -rr, 0.0, rr))
            }
            "drain" -> {
                val rr = minOf(w, d) / 2
                listOf(Prim.Circle(0.0, 0.0, rr, fill = true), Prim.Circle(0.0, 0.0, rr * 0.5), Prim.Line(-rr * 0.5, 0.0, rr * 0.5, 0.0))
            }
            "gas_valve" -> listOf(Prim.Line(l, t, r, b), Prim.Line(l, b, r, t), Prim.Line(l, t, l, b), Prim.Line(r, t, r, b), Prim.Circle(0.0, 0.0, minOf(w, d) * 0.15, fill = true))
            "water_heater" -> listOf(Prim.Circle(0.0, 0.0, minOf(w, d) / 2, fill = true), Prim.Circle(0.0, 0.0, minOf(w, d) * 0.3), Prim.Line(0.0, -minOf(w, d) * 0.3, 0.0, minOf(w, d) * 0.3))
            "boiler" -> listOf(body, Prim.Circle(0.0, 0.0, minOf(w, d) * 0.3), Prim.Line(l, t + d * 0.2, r, t + d * 0.2))
            "radiator" -> listOf(body) + (1 until 8).map { i -> Prim.Line(l + w * i / 8, t, l + w * i / 8, b) }
            else -> listOf(body, Prim.Line(l, t, r, b), Prim.Line(l, b, r, t))
        }
    }

    private fun diningSet(item: Item): List<Prim> {
        val w = item.width
        val d = item.depth
        val chair = 42.0
        val tw = maxOf(w - 2 * 10, 40.0)
        val td = maxOf(d - 2 * (chair - 6), 40.0)
        if (item.type == "round_table") {
            val tr = minOf(w, d) / 2 - chair + 6
            val chairs = (0 until 4).map { i ->
                val a = Math.toRadians(45.0 + 90.0 * i)
                val cr = tr + chair / 2 - 4
                Prim.Rect(cos(a) * cr - chair / 2 * 0.9, sin(a) * cr - chair / 2 * 0.9, chair * 0.9, chair * 0.9, 8.0, fill = true)
            }
            return chairs + Prim.Circle(0.0, 0.0, maxOf(tr, 20.0), fill = true)
        }
        val perSide = if (item.type == "dining6") 3 else 2
        val chairs = mutableListOf<Prim>()
        val spacing = tw / perSide
        for (i in 0 until perSide) {
            val cx = -tw / 2 + spacing * (i + 0.5)
            chairs += Prim.Rect(cx - chair / 2 + 2, -d / 2, chair - 4, chair, 8.0, fill = true)
            chairs += Prim.Rect(cx - chair / 2 + 2, d / 2 - chair, chair - 4, chair, 8.0, fill = true)
        }
        return chairs + Prim.Rect(-tw / 2, -td / 2, tw, td, 4.0, fill = true)
    }
}
