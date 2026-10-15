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
    /**
     * Every floor of a multi-storey plan, empty for a single floor. The floor at [floorIndex] is only a
     * placeholder (id and name): its content is the top-level walls, rooms and so on above, so the
     * editing code and older readers of the format only ever see one floor.
     */
    val floors: List<Floor> = emptyList(),
    val floorIndex: Int = 0,
    /** Ceiling height in world units, for paint estimates and the 3D view. */
    val wallHeight: Double = 270.0,
    val costs: Costs = Costs(),
    /** Placement of a photo of a paper plan under the drawing; the image itself is kept next to the project file. */
    val background: Background? = null,
    /** Direction of north on the plan, in degrees clockwise from the top of the screen. */
    val north: Double = 0.0,
    /** Latitude of the home in degrees (positive north), for the sunlight view. */
    val latitude: Double = 35.7,
    /** Asking price of the home, for the home buyer summary; null when not set. */
    val price: Double? = null,
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
    /** Height of a low wall (counter, railing) in world units; null for a full-height wall. */
    val height: Double? = null,
    /**
     * Curve of the wall: how far the middle of the arc sits from the straight line a→b, measured along its
     * left-hand normal (perp of a→b). 0 for a straight wall.
     */
    val bulge: Double = 0.0,
) {
    val a: P get() = P.of(points[0])
    val isCurved: Boolean get() = kotlin.math.abs(bulge) >= 0.5
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
    /** null for a single swing door, or one of [Door.DOUBLE], [Door.SLIDING]. */
    val style: String? = null,
) : Opening {
    companion object {
        const val DOUBLE = "double"
        const val SLIDING = "sliding"
    }
}

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
    /** A locked item stays where it is: it can't be dragged, turned or resized until it is unlocked. */
    val locked: Boolean = false,
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

/** One storey of a building; see [Project.floors]. */
@Serializable
data class Floor(
    val id: String,
    val name: String,
    val walls: List<Wall> = emptyList(),
    val doors: List<Door> = emptyList(),
    val windows: List<PlanWindow> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val items: List<Item> = emptyList(),
    val dimensions: List<Dimension> = emptyList(),
    val labels: List<Label> = emptyList(),
)

/** Unit prices for the materials estimate, per m², litre and metre; 0 means not set. */
@Serializable
data class Costs(
    val floorPerM2: Double = 0.0,
    val paintPerLiter: Double = 0.0,
    val skirtingPerM: Double = 0.0,
    /** Extra floor covering for cuts, in percent. */
    val wastePercent: Double = 10.0,
)

/** Where the background photo sits: its top-left corner at ([x], [y]), [scale] world units per image pixel. */
@Serializable
data class Background(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val scale: Double = 1.0,
    val opacity: Double = 0.5,
)

/** A named snapshot of a whole project, such as "Before renovation". */
@Serializable
data class SavedVersion(val name: String, val time: Long, val project: Project)
