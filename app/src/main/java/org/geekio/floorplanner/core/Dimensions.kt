package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Dimension
import org.geekio.floorplanner.model.Project

/** Where a dimension line is drawn: the measured line [start]-[end] and the extension lines from [fromA], [fromB]. */
data class DimensionGeometry(val fromA: P, val fromB: P, val start: P, val end: P, val normal: P) {
    val mid: P get() = (start + end) * 0.5

    /** 1 when the line sits on the normal side of the measured points, -1 on the other side, 0 on them. */
    val side: Double get() = Math.signum((start - fromA).dot(normal))

    /** Where the length label goes: [distance] off the middle of the line, away from the measured points. */
    fun labelAt(distance: Double): P = mid + normal * (distance * (if (side == 0.0) -1.0 else side))
}

/** Bounding box of the walls, used for the overall width and height. */
data class Extent(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
    val width get() = maxX - minX
    val height get() = maxY - minY
}

object Dimensions {
    fun create(a: P, b: P, offset: Double = 0.0) =
        Dimension(generateId("dim"), listOf(a.rounded().toList(), b.rounded().toList()), roundTo(offset, 1))

    fun geometry(d: Dimension): DimensionGeometry? = geometry(d.a, d.b, d.offset)

    fun geometry(a: P, b: P, offset: Double): DimensionGeometry? {
        if (a.dist(b) < 1e-6) return null
        val n = (b - a).normalized().perp()
        return DimensionGeometry(a, b, a + n * offset, b + n * offset, n)
    }

    fun hit(p: Project, point: P, tolerance: Double): Dimension? =
        p.dimensions.mapNotNull { d -> geometry(d)?.let { d to projectPointOnSegment(point, it.start, it.end).distance } }
            .filter { it.second <= tolerance }
            .minByOrNull { it.second }?.first

    /** Offset that puts the dimension line of [d] through [point]. */
    fun offsetThrough(d: Dimension, point: P): Double {
        val n = (d.b - d.a).normalized().perp()
        return roundTo((point - d.a).dot(n), 1)
    }

    fun wallExtent(p: Project): Extent? {
        if (p.walls.isEmpty()) return null
        val pts = p.walls.flatMap { listOf(it.a, it.b) }
        val pad = p.walls.maxOf { it.thickness } / 2
        return Extent(pts.minOf { it.x } - pad, pts.minOf { it.y } - pad, pts.maxOf { it.x } + pad, pts.maxOf { it.y } + pad)
    }

    /**
     * Overall width (above the plan) and height (left of it) of the walls' outer faces,
     * placed [gap] units away from the plan. Empty when the plan has no walls.
     */
    fun overall(p: Project, gap: Double): List<DimensionGeometry> {
        val e = wallExtent(p) ?: return emptyList()
        if (e.width < 1 || e.height < 1) return emptyList()
        val top = geometry(P(e.minX, e.minY), P(e.maxX, e.minY), -gap)
        val left = geometry(P(e.minX, e.maxY), P(e.minX, e.minY), -gap)
        return listOfNotNull(top, left)
    }
}
