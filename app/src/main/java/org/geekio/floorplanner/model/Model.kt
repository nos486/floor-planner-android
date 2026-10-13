package org.geekio.floorplanner.model

import org.geekio.floorplanner.core.P
import kotlinx.serialization.Serializable

/**
 * Project file format. Compatible with the JSON files of the FloorPlanner 2D web app.
 * World units: [Scale.unitsPerMeter] units = 1 m (100 by default, so 1 unit = 1 cm).
 */
@Serializable
data class Project(
    val projectName: String = "My Home Plan",
    val scale: Scale = Scale(),
    val walls: List<Wall> = emptyList(),
    val doors: List<Door> = emptyList(),
    val windows: List<PlanWindow> = emptyList(),
    val rooms: List<Room> = emptyList(),
) {
    fun wall(id: String) = walls.firstOrNull { it.id == id }
    fun door(id: String) = doors.firstOrNull { it.id == id }
    fun window(id: String) = windows.firstOrNull { it.id == id }
    fun room(id: String) = rooms.firstOrNull { it.id == id }

    val totalAreaM2: Double get() = rooms.sumOf { it.areaM2 }
}

@Serializable
data class Scale(
    val unitsPerMeter: Double = 100.0,
    val gridCm: Double = 20.0,
)

@Serializable
data class Wall(
    val id: String,
    val points: List<List<Double>>,
    val thickness: Double = 20.0,
) {
    val a: P get() = P.of(points[0])
    val b: P get() = P.of(points[1])
    val length: Double get() = a.dist(b)

    fun withPoints(a: P, b: P) = copy(points = listOf(a.rounded().toList(), b.rounded().toList()))

    companion object {
        fun create(id: String, a: P, b: P, thickness: Double) =
            Wall(id, listOf(a.rounded().toList(), b.rounded().toList()), thickness)
    }
}

/** An opening (door or window) placed along a wall, [offset] is the distance of its centre from the wall start. */
sealed interface Opening {
    val id: String
    val wallId: String
    val offset: Double
    val width: Double
}

@Serializable
data class Door(
    override val id: String,
    override val wallId: String,
    override val offset: Double,
    override val width: Double = 90.0,
    /** When true the hinge sits at the wall-end side of the opening. */
    val flipped: Boolean = false,
    /** 1 or -1: which side of the wall the door swings to. */
    val side: Int = 1,
) : Opening

@Serializable
data class PlanWindow(
    override val id: String,
    override val wallId: String,
    override val offset: Double,
    override val width: Double = 120.0,
) : Opening

@Serializable
data class Room(
    val id: String,
    val name: String,
    val points: List<List<Double>> = emptyList(),
    val areaM2: Double = 0.0,
    val centroid: List<Double> = emptyList(),
) {
    val polygon: List<P> get() = points.map { P.of(it) }
    val center: P? get() = if (centroid.size >= 2) P.of(centroid) else null
}
