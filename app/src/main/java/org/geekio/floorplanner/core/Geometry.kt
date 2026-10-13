package org.geekio.floorplanner.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

/** A 2D point / vector in world units (1 unit = 1 cm by default, y axis points down). */
data class P(val x: Double, val y: Double) {
    operator fun plus(o: P) = P(x + o.x, y + o.y)
    operator fun minus(o: P) = P(x - o.x, y - o.y)
    operator fun times(k: Double) = P(x * k, y * k)
    operator fun unaryMinus() = P(-x, -y)

    fun dist(o: P) = hypot(o.x - x, o.y - y)
    fun length() = hypot(x, y)
    fun dot(o: P) = x * o.x + y * o.y
    fun cross(o: P) = x * o.y - y * o.x

    fun normalized(): P {
        val l = length()
        return if (l == 0.0) ZERO else P(x / l, y / l)
    }

    /** Perpendicular vector (rotated +90 degrees in screen space). */
    fun perp() = P(-y, x)

    fun rounded(decimals: Int = 1): P = P(roundTo(x, decimals), roundTo(y, decimals))

    fun toList(): List<Double> = listOf(x, y)

    companion object {
        val ZERO = P(0.0, 0.0)
        fun of(list: List<Double>): P = P(list.getOrElse(0) { 0.0 }, list.getOrElse(1) { 0.0 })
    }
}

fun roundTo(v: Double, decimals: Int): Double {
    var f = 1.0
    repeat(decimals) { f *= 10 }
    return (v * f).roundToLong() / f
}

data class Projection(val point: P, val t: Double, val distance: Double)

/** Projects [p] onto segment [a]-[b]; t is clamped to [0, 1]. */
fun projectPointOnSegment(p: P, a: P, b: P): Projection {
    val ab = b - a
    val lenSq = ab.dot(ab)
    if (lenSq == 0.0) return Projection(a, 0.0, p.dist(a))
    val t = ((p - a).dot(ab) / lenSq).coerceIn(0.0, 1.0)
    val proj = a + ab * t
    return Projection(proj, t, p.dist(proj))
}

/** Shoelace signed area. Positive for clockwise-on-screen polygons (y down). */
fun signedPolygonArea(points: List<P>): Double {
    val n = points.size
    if (n < 3) return 0.0
    var sum = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        sum += points[i].x * points[j].y - points[j].x * points[i].y
    }
    return sum / 2
}

fun polygonArea(points: List<P>) = abs(signedPolygonArea(points))

fun polygonCentroid(points: List<P>): P {
    val n = points.size
    if (n == 0) return P.ZERO
    if (n == 1) return points[0]
    if (n == 2) return (points[0] + points[1]) * 0.5
    var cx = 0.0
    var cy = 0.0
    var area = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        val a = points[i].x * points[j].y - points[j].x * points[i].y
        area += a
        cx += (points[i].x + points[j].x) * a
        cy += (points[i].y + points[j].y) * a
    }
    area *= 0.5
    if (abs(area) < 1e-7) {
        return P(points.sumOf { it.x } / n, points.sumOf { it.y } / n)
    }
    return P(cx / (6 * area), cy / (6 * area))
}

/** Ray casting point-in-polygon test. */
fun pointInPolygon(point: P, vs: List<P>): Boolean {
    var inside = false
    var j = vs.size - 1
    for (i in vs.indices) {
        val xi = vs[i].x
        val yi = vs[i].y
        val xj = vs[j].x
        val yj = vs[j].y
        val intersect = ((yi > point.y) != (yj > point.y)) &&
            (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

/** Intersection point of segments [p1,p2] and [p3,p4], or null when they don't cross (or are parallel). */
fun segmentIntersection(p1: P, p2: P, p3: P, p4: P, tolerance: Double = 1e-5): P? {
    val d1 = p2 - p1
    val d2 = p4 - p3
    val cross = d1.cross(d2)
    if (abs(cross) < tolerance) return null
    val d = p3 - p1
    val t1 = d.cross(d2) / cross
    val t2 = d.cross(d1) / cross
    if (t1 >= -tolerance && t1 <= 1 + tolerance && t2 >= -tolerance && t2 <= 1 + tolerance) {
        return p1 + d1 * t1.coerceIn(0.0, 1.0)
    }
    return null
}

private val idChars = "abcdefghijklmnopqrstuvwxyz0123456789"

fun generateId(prefix: String): String {
    val time = System.currentTimeMillis().toString(36)
    val rand = (1..5).map { idChars.random() }.joinToString("")
    return "${prefix}_${time}_$rand"
}
