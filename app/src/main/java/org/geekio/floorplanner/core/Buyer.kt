package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Floor
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Room
import org.geekio.floorplanner.model.Wall
import kotlin.math.atan2
import kotlin.math.tan

/** A room's area measured to the wall centre lines ([areaM2]) and to the wall faces ([netM2]). */
data class RoomArea(val name: String, val areaM2: Double, val netM2: Double)

/**
 * The figures a home buyer asks for. [netM2] is the usable floor between the walls; [grossM2] adds the
 * walls (outer walls in full, inner walls once), like the area on a deed. Prices per m² are null when
 * the price isn't set.
 */
data class BuyerSummary(
    val netM2: Double,
    val grossM2: Double,
    val rooms: List<RoomArea>,
    val price: Double?,
) {
    val pricePerNetM2: Double? get() = price?.takeIf { netM2 > 0 }?.let { it / netM2 }
    val pricePerGrossM2: Double? get() = price?.takeIf { grossM2 > 0 }?.let { it / grossM2 }
}

object Buyer {
    fun summary(p: Project): BuyerSummary {
        val upm = p.scale.unitsPerMeter
        var gross = 0.0
        val rooms = mutableListOf<RoomArea>()
        for (f in Floors.all(p)) {
            for (room in f.rooms) {
                if (room.polygon.size < 3) continue
                rooms += RoomArea(room.name, room.areaM2, netArea(f, room) / (upm * upm))
            }
            gross += grossArea(f) / (upm * upm)
        }
        return BuyerSummary(roundTo(rooms.sumOf { it.netM2 }, 2), roundTo(gross, 2), rooms, p.price?.takeIf { it > 0 })
    }

    /** Thickness of the wall that the room side a→b lies on (0 when it's on none). */
    private fun thicknessAt(walls: List<Wall>, a: P, b: P): Double {
        val mid = (a + b) * 0.5
        return walls.filter { Walls.distance(it, mid) < 1.0 }.maxOfOrNull { it.thickness } ?: 0.0
    }

    /**
     * Area of [room] inside the wall faces: the centre-line polygon pulled in by half the thickness of
     * each side's wall, A − Σ len·d + Σ d₁·d₂·cot(θ/2) over the corners (θ the inside angle).
     */
    fun netArea(f: Floor, room: Room): Double {
        val poly = room.polygon
        val n = poly.size
        val ccw = signedPolygonArea(poly) > 0
        val d = (0 until n).map { thicknessAt(f.walls, poly[it], poly[(it + 1) % n]) / 2 }
        var area = polygonArea(poly)
        for (i in 0 until n) area -= poly[i].dist(poly[(i + 1) % n]) * d[i]
        for (i in 0 until n) {
            val prev = poly[(i - 1 + n) % n]
            val cur = poly[i]
            val next = poly[(i + 1) % n]
            val turn = (cur - prev).cross(next - cur).let { c -> atan2(c, (cur - prev).dot(next - cur)) }
            // Inside angle from the turn at this corner, whichever way the polygon runs.
            val inside = Math.PI - if (ccw) turn else -turn
            if (inside < 0.05 || inside > 2 * Math.PI - 0.05) continue
            area += d[(i - 1 + n) % n] * d[i] / tan(inside / 2)
        }
        return area.coerceAtLeast(0.0)
    }

    /** Rooms at the wall centre lines plus the outer half of every outside wall. */
    fun grossArea(f: Floor): Double {
        val polys = f.rooms.map { it.polygon }.filter { it.size >= 3 }
        var area = polys.sumOf { polygonArea(it) }
        for ((ri, poly) in polys.withIndex()) {
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                val t = thicknessAt(f.walls, a, b)
                if (t <= 0) continue
                val mid = (a + b) * 0.5
                val nrm = (b - a).normalized().perp() * (t / 2 + 5)
                val outside = listOf(mid + nrm, mid - nrm).firstOrNull { !pointInPolygon(it, poly) } ?: continue
                if (polys.withIndex().none { (j, other) -> j != ri && pointInPolygon(outside, other) }) {
                    area += a.dist(b) * t / 2
                }
            }
        }
        return area
    }
}
