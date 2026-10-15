package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Wall
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Wall centre lines as polylines, measured by distance along them. */
object Walls {
    /** The centre line of [wall] from its start to its end: two points, or an arc as a polyline. */
    fun path(wall: Wall): List<P> {
        if (!wall.isCurved || wall.a.dist(wall.b) < 1) return listOf(wall.a, wall.b)
        val arc = arc(wall)
        val n = (Math.abs(arc.sweep) * arc.radius / ARC_STEP).toInt().coerceIn(8, 64)
        return (0..n).map { i ->
            val t = arc.start + arc.sweep * i / n
            P(arc.center.x + arc.radius * cos(t), arc.center.y + arc.radius * sin(t))
        }.let { pts -> listOf(wall.a) + pts.subList(1, pts.lastIndex) + wall.b }
    }

    data class Arc(val center: P, val radius: Double, val start: Double, val sweep: Double)

    /** The circle arc of a curved wall. */
    fun arc(wall: Wall): Arc {
        val c = wall.a.dist(wall.b)
        val s = wall.bulge
        val n = (wall.b - wall.a).normalized().perp()
        val mid = (wall.a + wall.b) * 0.5
        val r = (c * c / 4 + s * s) / (2 * Math.abs(s))
        val center = mid + n * (s - Math.signum(s) * r)
        val apex = mid + n * s
        fun ang(p: P) = atan2(p.y - center.y, p.x - center.x)
        val start = ang(wall.a)
        var half = ang(apex) - start
        while (half > Math.PI) half -= 2 * Math.PI
        while (half <= -Math.PI) half += 2 * Math.PI
        return Arc(center, r, start, 2 * half)
    }

    /** The middle of the wall's centre line (the top of the arc), where the bend handle sits. */
    fun apex(wall: Wall): P = (wall.a + wall.b) * 0.5 + (wall.b - wall.a).normalized().perp() * wall.bulge

    /** Bulge that makes the wall's arc pass through [point] at its middle. */
    fun bulgeThrough(wall: Wall, point: P): Double {
        val n = (wall.b - wall.a).normalized().perp()
        val s = (point - (wall.a + wall.b) * 0.5).dot(n)
        val max = wall.a.dist(wall.b) / 2
        return roundTo(s.coerceIn(-max, max), 0).let { if (Math.abs(it) < 2) 0.0 else it }
    }

    /** Distance from [point] to the wall's centre line. */
    fun distance(wall: Wall, point: P): Double {
        val path = path(wall)
        return (0 until path.lastIndex).minOf { projectPointOnSegment(point, path[it], path[it + 1]).distance }
    }

    private const val ARC_STEP = 15.0

    fun length(wall: Wall): Double = length(path(wall))

    fun length(path: List<P>): Double = (0 until path.lastIndex).sumOf { path[it].dist(path[it + 1]) }

    /** The point [s] units along [path], or null for an empty path. */
    fun pointAt(path: List<P>, s: Double): P? {
        if (path.isEmpty()) return null
        var left = s
        for (i in 0 until path.lastIndex) {
            val seg = path[i].dist(path[i + 1])
            if (left <= seg) return if (seg == 0.0) path[i] else path[i] + (path[i + 1] - path[i]) * (left / seg)
            left -= seg
        }
        return path.last()
    }

    /** The part of [path] between distances [from] and [to], with its corners. */
    fun slice(path: List<P>, from: Double, to: Double): List<P> {
        if (path.size < 2 || to <= from) return emptyList()
        val out = mutableListOf(pointAt(path, from) ?: return emptyList())
        var at = 0.0
        for (i in 1 until path.lastIndex + 1) {
            at += path[i - 1].dist(path[i])
            if (at > from && at < to && i < path.lastIndex) out += path[i]
        }
        out += pointAt(path, to) ?: return emptyList()
        return out
    }
}
