package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Where the sun is: [azimuth] in degrees clockwise from north, [elevation] in degrees above the horizon. */
data class SunPosition(val azimuth: Double, val elevation: Double) {
    val isUp: Boolean get() = elevation > 0.5
}

/** Sunlight falling through one window onto the floor, as a quad in world units. */
data class LightPatch(val windowId: String, val roomId: String, val polygon: List<P>)

/** A window that lets light into room [roomId]; [inward] is the unit normal pointing into that room. */
data class SunWindow(val id: String, val g: OpeningGeometry, val roomId: String, val inward: P)

/** Hours of direct sun a room gets through its windows on one day. */
data class RoomSun(val roomId: String, val name: String, val hours: Double)

/**
 * A simple sun model for the sunlight view: the sun's position from latitude, day of year and solar time,
 * and the patches of light its rays make through the windows. Neighbouring buildings are not considered.
 */
object Sun {
    /** Sill and top of a window above the floor, in cm (as in the estimates). */
    const val SILL_CM = Estimates.WINDOW_SILL
    const val TOP_CM = Estimates.WINDOW_SILL + Estimates.WINDOW_HEIGHT

    /** [hour] is local solar time (12 = noon), [day] the day of the year (1..365). */
    fun position(latitude: Double, day: Int, hour: Double): SunPosition {
        val phi = Math.toRadians(latitude)
        val decl = Math.toRadians(23.44) * sin(Math.toRadians(360.0 / 365 * (284 + day)))
        val h = Math.toRadians(15 * (hour - 12))
        val elev = asin((sin(phi) * sin(decl) + cos(phi) * cos(decl) * cos(h)).coerceIn(-1.0, 1.0))
        // Azimuth measured from south, positive to the west; then turned to "from north".
        val fromSouth = atan2(sin(h), cos(h) * sin(phi) - tan(decl) * cos(phi))
        val az = (Math.toDegrees(fromSouth) + 180).mod(360.0)
        return SunPosition(az, Math.toDegrees(elev))
    }

    /** Unit vector on the plan (y down) pointing from the plan towards the sun's azimuth. */
    fun towardSun(p: Project, sun: SunPosition): P {
        val a = Math.toRadians(p.north + sun.azimuth)
        return P(sin(a), -cos(a))
    }

    /** Unit vector on the plan pointing north. */
    fun northVector(p: Project): P {
        val a = Math.toRadians(p.north)
        return P(sin(a), -cos(a))
    }

    /**
     * For each window, the room it lights and the unit normal pointing into that room. Windows between two
     * rooms, or with no room on either side, are skipped.
     */
    fun windowRooms(p: Project): List<SunWindow> = p.windows.mapNotNull { win ->
        val wall = p.wall(win.wallId) ?: return@mapNotNull null
        if (wall.isCurved) return@mapNotNull null
        val g = PlanOps.openingGeometry(wall, win) ?: return@mapNotNull null
        val reach = g.wallThickness / 2 + 5
        val front = p.rooms.firstOrNull { it.polygon.size >= 3 && pointInPolygon(g.center + g.normal * reach, it.polygon) }
        val back = p.rooms.firstOrNull { it.polygon.size >= 3 && pointInPolygon(g.center - g.normal * reach, it.polygon) }
        when {
            front != null && back == null -> SunWindow(win.id, g, front.id, g.normal)
            back != null && front == null -> SunWindow(win.id, g, back.id, -g.normal)
            else -> null
        }
    }

    /** Light patches on the floor for the sun at [sun]. Empty at night. */
    fun patches(p: Project, sun: SunPosition): List<LightPatch> {
        if (!sun.isUp) return emptyList()
        val s = towardSun(p, sun)
        val k = p.scale.unitsPerMeter / 100 / tan(Math.toRadians(sun.elevation))
        val ray = -s
        return windowRooms(p).mapNotNull { w ->
            if (ray.dot(w.inward) <= 0.02) return@mapNotNull null
            // Light starts at the inner face of the wall.
            val a = w.g.start + w.inward * (w.g.wallThickness / 2)
            val b = w.g.end + w.inward * (w.g.wallThickness / 2)
            val near = ray * (SILL_CM * k)
            val far = ray * (TOP_CM * k)
            LightPatch(w.id, w.roomId, listOf(a + near, b + near, b + far, a + far))
        }
    }

    /** Hours of direct sun per room on [day], sampled every quarter hour. Rooms with no outside window get 0. */
    fun hours(p: Project, day: Int): List<RoomSun> {
        val lit = mutableMapOf<String, Int>()
        val windows = windowRooms(p)
        var t = 0.125
        while (t < 24) {
            val sun = position(p.latitude, day, t)
            if (sun.isUp) {
                val ray = -towardSun(p, sun)
                windows.filter { ray.dot(it.inward) > 0.02 }.map { it.roomId }.toSet().forEach { lit[it] = (lit[it] ?: 0) + 1 }
            }
            t += 0.25
        }
        return p.rooms.map { RoomSun(it.id, it.name, (lit[it.id] ?: 0) * 0.25) }
    }

    /** Day of the year for the middle of [month] (1..12). */
    fun midMonthDay(month: Int): Int = intArrayOf(15, 46, 74, 105, 135, 166, 196, 227, 258, 288, 319, 349)[(month - 1).coerceIn(0, 11)]
}
