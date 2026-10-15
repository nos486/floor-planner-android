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
    val items: List<Item> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val labels: List<Label> = emptyList(),
) {
    fun wall(id: String) = walls.firstOrNull { it.id == id }
    fun door(id: String) = doors.firstOrNull { it.id == id }
    fun window(id: String) = windows.firstOrNull { it.id == id }
    fun room(id: String) = rooms.firstOrNull { it.id == id }
    fun item(id: String) = items.firstOrNull { it.id == id }
    fun dimension(id: String) = dimensions.firstOrNull { it.id == id }
    fun label(id: String) = labels.firstOrNull { it.id == id }

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
    /** Fill colour as "#rrggbb", null for the automatic colour. */
    val color: String? = null,
    /** Floor material key (see FloorPatterns), null for a plain fill. */
    val floor: String? = null,
) {
    val polygon: List<P> get() = points.map { P.of(it) }
    val center: P? get() = if (centroid.size >= 2) P.of(centroid) else null
}

/** A piece of furniture or fixture: [type] is a key of the furniture catalog, ([x], [y]) its centre, [rotation] in degrees. */
@Serializable
data class Item(
    val id: String,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val depth: Double,
    val rotation: Double = 0.0,
)

/** A measurement line between two points, drawn [offset] units to the side (along the normal of a→b). */
@Serializable
data class Dimension(
    val id: String,
    val points: List<List<Double>>,
    val offset: Double = 0.0,
) {
    val a: P get() = P.of(points[0])
    val b: P get() = P.of(points[1])
    val length: Double get() = a.dist(b)
}

/**
 * A text note on the plan, centred on ([x], [y]). [size] is the text height in world units, so it scales with
 * the plan. When [arrow] is set, an arrow points from the text to that point.
 */
@Serializable
data class Label(
    val id: String,
    val text: String,
    val x: Double,
    val y: Double,
    val size: Double = 25.0,
    val arrow: List<Double>? = null,
) {
    val anchor: P get() = P(x, y)
    val tip: P? get() = arrow?.takeIf { it.size >= 2 }?.let { P.of(it) }
}
