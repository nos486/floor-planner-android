package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Room
import org.geekio.floorplanner.model.Scale
import org.geekio.floorplanner.model.Wall
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Finds enclosed rooms in a set of walls: builds a planar graph (splitting walls at crossings
 * and T-junctions), walks every face with the next-leftmost-edge rule and keeps the interior ones.
 */
object RoomDetector {
    private const val NODE_EPS = 0.5
    private const val SPLIT_TOLERANCE = 1.0
    private const val MIN_AREA_M2 = 0.1

    private data class Seg(val a: P, val b: P)
    private data class Edge(val target: Int, val angle: Double)

    fun detect(walls: List<Wall>, scale: Scale, existing: List<Room> = emptyList()): List<Room> {
        if (walls.size < 3) return emptyList()
        val raw = walls.filter { it.points.size >= 2 }
            .flatMap { w -> Walls.path(w).zipWithNext { a, b -> Seg(a, b) } }
            .filter { it.a.dist(it.b) > 1 }
        if (raw.size < 3) return emptyList()

        val segments = splitAll(raw)
        val (nodes, adj) = buildGraph(segments)
        val polygons = extractFaces(nodes, adj)

        val upm = if (scale.unitsPerMeter > 0) scale.unitsPerMeter else 100.0
        val usedIds = mutableSetOf<String>()
        val detected = mutableListOf<Room>()

        for (poly in polygons) {
            val areaM2 = polygonArea(poly) / (upm * upm)
            if (areaM2 < MIN_AREA_M2) continue
            val c = polygonCentroid(poly)

            // Keep the name/id of the room that used to be here.
            val match = existing.firstOrNull { er ->
                val ec = er.center
                er.id !in usedIds && ec != null && (ec.dist(c) < upm * 1.5 || pointInPolygon(ec, poly))
            }
            val id = match?.id ?: generateId("room")
            usedIds += id
            detected += Room(
                id = id,
                name = match?.name ?: "",
                points = poly.map { it.rounded().toList() },
                areaM2 = roundTo(areaM2, 2),
                centroid = c.rounded().toList(),
                color = match?.color,
                floor = match?.floor,
            )
        }

        detected.sortWith { r1, r2 ->
            val c1 = r1.center!!
            val c2 = r2.center!!
            if (abs(c1.y - c2.y) > 50) c1.y.compareTo(c2.y) else c1.x.compareTo(c2.x)
        }

        // Give unnamed rooms a default name that isn't taken yet.
        val names = detected.map { it.name }.filter { it.isNotBlank() }.toMutableSet()
        var n = 1
        return detected.map { room ->
            if (room.name.isNotBlank()) room
            else {
                while ("Room $n" in names) n++
                val name = "Room $n"
                names += name
                room.copy(name = name)
            }
        }
    }

    /** Splits every segment at the points where other segments cross it or end on it. */
    private fun splitAll(segments: List<Seg>): List<Seg> {
        val out = mutableListOf<Seg>()
        for ((i, s) in segments.withIndex()) {
            val cuts = mutableListOf<P>()
            for ((j, o) in segments.withIndex()) {
                if (i == j) continue
                segmentIntersection(s.a, s.b, o.a, o.b)?.let { cuts += it }
                for (pt in listOf(o.a, o.b)) {
                    val proj = projectPointOnSegment(pt, s.a, s.b)
                    if (proj.distance <= SPLIT_TOLERANCE) cuts += proj.point
                }
            }
            val dir = s.b - s.a
            val lenSq = dir.dot(dir)
            val ordered = cuts
                .filter { it.dist(s.a) > SPLIT_TOLERANCE && it.dist(s.b) > SPLIT_TOLERANCE }
                .sortedBy { (it - s.a).dot(dir) / lenSq }
            var prev = s.a
            for (c in ordered) {
                if (c.dist(prev) <= SPLIT_TOLERANCE) continue
                out += Seg(prev, c)
                prev = c
            }
            out += Seg(prev, s.b)
        }
        return out
    }

    private fun buildGraph(segments: List<Seg>): Pair<List<P>, Map<Int, List<Edge>>> {
        val nodes = mutableListOf<P>()
        fun nodeOf(p: P): Int {
            val idx = nodes.indexOfFirst { it.dist(p) <= NODE_EPS }
            if (idx >= 0) return idx
            nodes += p
            return nodes.size - 1
        }

        val adj = LinkedHashMap<Int, MutableList<Edge>>()
        for (s in segments) {
            val u = nodeOf(s.a)
            val v = nodeOf(s.b)
            if (u == v) continue
            val pu = nodes[u]
            val pv = nodes[v]
            val eu = adj.getOrPut(u) { mutableListOf() }
            val ev = adj.getOrPut(v) { mutableListOf() }
            if (eu.none { it.target == v }) eu += Edge(v, atan2(pv.y - pu.y, pv.x - pu.x))
            if (ev.none { it.target == u }) ev += Edge(u, atan2(pu.y - pv.y, pu.x - pv.x))
        }
        val sorted = adj.mapValues { (_, list) -> list.sortedBy { it.angle } }
        return nodes to sorted
    }

    private fun extractFaces(nodes: List<P>, adj: Map<Int, List<Edge>>): List<List<P>> {
        val visited = HashSet<Long>()
        fun key(u: Int, v: Int) = (u.toLong() shl 32) or v.toLong()
        val faces = mutableListOf<List<P>>()

        for ((u, outgoing) in adj) {
            for (edge in outgoing) {
                val v = edge.target
                if (key(u, v) in visited) continue

                val cycle = mutableListOf(u)
                var cu = u
                var cv = v
                var valid = true
                while (true) {
                    visited += key(cu, cv)
                    cycle += cv
                    val vOut = adj[cv]
                    if (vOut.isNullOrEmpty()) { valid = false; break }
                    val rev = vOut.indexOfFirst { it.target == cu }
                    if (rev == -1) { valid = false; break }
                    val next = vOut[(rev - 1 + vOut.size) % vOut.size]
                    cu = cv
                    cv = next.target
                    if (cu == u && cv == v) break
                    if (cycle.size > nodes.size * 2) { valid = false; break }
                }

                if (valid && cycle.size > 3) {
                    val poly = cycle.dropLast(1).map { nodes[it] }
                    // With y pointing down, interior faces come out with positive signed area.
                    if (signedPolygonArea(poly) > 0) faces += poly
                }
            }
        }

        val unique = mutableListOf<List<P>>()
        for (f in faces) {
            val area = polygonArea(f)
            val c = polygonCentroid(f)
            val dup = unique.any { abs(polygonArea(it) - area) < 1 && polygonCentroid(it).dist(c) < 5 }
            if (!dup) unique += f
        }
        return unique
    }
}
