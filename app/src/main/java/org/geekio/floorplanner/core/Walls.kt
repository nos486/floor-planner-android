package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Wall

/** Wall centre lines as polylines, measured by distance along them. */
object Walls {
    /** The centre line of [wall] from its start to its end. */
    fun path(wall: Wall): List<P> = listOf(wall.a, wall.b)

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
