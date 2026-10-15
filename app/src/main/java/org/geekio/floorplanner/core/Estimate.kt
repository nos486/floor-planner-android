package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Floor
import org.geekio.floorplanner.model.Opening
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Room

/** Quantities for one room. Areas in m², lengths in m. */
data class RoomEstimate(
    val floorName: String,
    val room: Room,
    val floorM2: Double,
    val perimeterM: Double,
    /** Wall area to paint: perimeter × height minus doors and windows. */
    val wallM2: Double,
    /** Perimeter minus door openings. */
    val skirtingM: Double,
)

data class Estimate(val rooms: List<RoomEstimate>, val wastePercent: Double) {
    val floorM2 get() = rooms.sumOf { it.floorM2 }
    /** Floor covering to buy, with the waste allowance. */
    val coveringM2 get() = floorM2 * (1 + wastePercent / 100)
    val wallM2 get() = rooms.sumOf { it.wallM2 }
    val paintLiters get() = wallM2 * Estimates.COATS / Estimates.COVERAGE_M2_PER_L
    val skirtingM get() = rooms.sumOf { it.skirtingM }
}

/** Material quantities and costs from the room shapes, wall height and openings. */
object Estimates {
    const val DOOR_HEIGHT = 210.0
    const val WINDOW_SILL = 90.0
    const val WINDOW_HEIGHT = 120.0
    const val COATS = 2
    /** Typical wall paint coverage per coat. */
    const val COVERAGE_M2_PER_L = 10.0

    fun estimate(p: Project): Estimate {
        val rooms = Floors.all(p).flatMap { f -> floorRooms(f, p.scale.unitsPerMeter, p.wallHeight) }
        return Estimate(rooms, p.costs.wastePercent)
    }

    private fun floorRooms(f: Floor, upm: Double, height: Double): List<RoomEstimate> = f.rooms.mapNotNull { room ->
        val poly = room.polygon
        if (poly.size < 3) return@mapNotNull null
        val perimeter = poly.indices.sumOf { poly[it].dist(poly[(it + 1) % poly.size]) }
        val doors = f.doors.filter { onBoundary(f, it, poly) }
        val windows = f.windows.filter { onBoundary(f, it, poly) }
        val openingArea = doors.sumOf { it.width * DOOR_HEIGHT } + windows.sumOf { it.width * WINDOW_HEIGHT }
        val wall = maxOf(0.0, perimeter * height - openingArea)
        RoomEstimate(
            floorName = f.name,
            room = room,
            floorM2 = room.areaM2,
            perimeterM = perimeter / upm,
            wallM2 = wall / (upm * upm),
            skirtingM = maxOf(0.0, perimeter - doors.sumOf { it.width }) / upm,
        )
    }

    /** True when the opening's centre sits on an edge of [poly] (the room it opens into). */
    fun onBoundary(f: Floor, o: Opening, poly: List<P>): Boolean {
        val wall = f.walls.firstOrNull { it.id == o.wallId } ?: return false
        val c = PlanOps.openingGeometry(wall, o)?.center ?: return false
        return poly.indices.any { projectPointOnSegment(c, poly[it], poly[(it + 1) % poly.size]).distance <= 1.0 }
    }

    data class Cost(val floor: Double, val paint: Double, val skirting: Double) {
        val total get() = floor + paint + skirting
    }

    fun cost(e: Estimate, p: Project) = Cost(
        floor = e.coveringM2 * p.costs.floorPerM2,
        paint = e.paintLiters * p.costs.paintPerLiter,
        skirting = e.skirtingM * p.costs.skirtingPerM,
    )
}
