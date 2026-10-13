package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Wall
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SnapType { NONE, GRID, ENDPOINT, ANGLE }

data class SnapResult(val point: P, val type: SnapType)

data class SnapOptions(
    val grid: Boolean = true,
    val endpoints: Boolean = true,
    val angles: Boolean = true,
)

/** Resolves endpoint snapping first, then 45-degree angle snapping, then grid snapping. */
object Snapper {
    private const val ANGLE_THRESHOLD_DEG = 6.0

    fun snapToGrid(p: P, gridSize: Double): P {
        if (gridSize <= 0) return p
        return P(Math.round(p.x / gridSize) * gridSize, Math.round(p.y / gridSize) * gridSize)
    }

    /** Closest wall endpoint within [radius] world units, skipping points close to [exclude]. */
    fun nearestEndpoint(p: P, walls: List<Wall>, radius: Double, exclude: P? = null): P? {
        var best: P? = null
        var bestD = radius
        for (w in walls) {
            for (ep in listOf(w.a, w.b)) {
                if (exclude != null && ep.dist(exclude) < 0.01) continue
                val d = p.dist(ep)
                if (d < bestD) {
                    bestD = d
                    best = ep
                }
            }
        }
        return best
    }

    fun snapToAngle(start: P, current: P): P? {
        val d = current - start
        val length = d.length()
        if (length < 1e-4) return null
        var deg = atan2(d.y, d.x) * 180 / PI
        if (deg < 0) deg += 360
        val target = (deg / 45).roundToInt() * 45.0
        val diff = abs(deg - target)
        if (diff > ANGLE_THRESHOLD_DEG && 360 - diff > ANGLE_THRESHOLD_DEG) return null
        val rad = target * PI / 180
        return P(start.x + length * cos(rad), start.y + length * sin(rad)).rounded(2)
    }

    fun resolve(
        point: P,
        walls: List<Wall>,
        gridSize: Double,
        endpointRadius: Double,
        options: SnapOptions,
        start: P? = null,
        exclude: P? = null,
    ): SnapResult {
        if (options.endpoints) {
            nearestEndpoint(point, walls, endpointRadius, exclude)?.let { return SnapResult(it, SnapType.ENDPOINT) }
        }
        if (options.angles && start != null) {
            snapToAngle(start, point)?.let { angled ->
                // Keep the angle but round the length to the grid so walls get tidy lengths.
                if (options.grid && gridSize > 0) {
                    val dir = (angled - start).normalized()
                    val len = Math.round(angled.dist(start) / gridSize) * gridSize
                    return SnapResult((start + dir * len).rounded(2), SnapType.ANGLE)
                }
                return SnapResult(angled, SnapType.ANGLE)
            }
        }
        if (options.grid) return SnapResult(snapToGrid(point, gridSize), SnapType.GRID)
        return SnapResult(point, SnapType.NONE)
    }
}
