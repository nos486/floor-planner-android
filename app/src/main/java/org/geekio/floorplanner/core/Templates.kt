package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall

enum class RoomKey { LIVING, KITCHEN, BEDROOM, MASTER_BEDROOM, BATHROOM, WC, STORAGE }

/** A ready-made plan to start from. [area] is its area in m² (to the wall centre lines), for display. */
data class Template(val key: String, val area: Int, val rooms: Int)

/** Ready-made apartment plans. Room names come from [names] so they can be localized. */
object Templates {
    val all = listOf(
        Template("studio", 35, 2),
        Template("one_bed", 60, 5),
        Template("two_bed", 90, 5),
        Template("three_bed", 126, 7),
    )

    private const val OUTER = 20.0
    private const val INNER = 12.0

    private class Builder(val w: Double, val h: Double) {
        val walls = mutableListOf<Wall>()
        val doors = mutableListOf<Door>()
        val windows = mutableListOf<PlanWindow>()
        val hints = mutableListOf<Pair<P, RoomKey>>()

        // Outer walls, clockwise from the top-left corner.
        val top = wall(0.0, 0.0, w, 0.0, OUTER)
        val right = wall(w, 0.0, w, h, OUTER)
        val bottom = wall(w, h, 0.0, h, OUTER)
        val left = wall(0.0, h, 0.0, 0.0, OUTER)

        fun wall(x1: Double, y1: Double, x2: Double, y2: Double, t: Double = INNER): String {
            val id = "w${walls.size + 1}"
            walls += Wall.create(id, P(x1, y1), P(x2, y2), t)
            return id
        }

        /** Door centred at [at] (distance from the wall's start point). */
        fun door(wall: String, at: Double, width: Double = 90.0, side: Int = 1, flipped: Boolean = false) {
            doors += Door("d${doors.size + 1}", wall, at, width, flipped, side)
        }

        fun window(wall: String, at: Double, width: Double) {
            windows += PlanWindow("win${windows.size + 1}", wall, at, width)
        }

        fun room(x: Double, y: Double, key: RoomKey) {
            hints += P(x, y) to key
        }

        fun build(name: String, names: (RoomKey) -> String): Project {
            var p = PlanOps.withRooms(Project(projectName = name, walls = walls, doors = doors, windows = windows))
            for ((pt, key) in hints) {
                p.rooms.firstOrNull { pointInPolygon(pt, it.polygon) }?.let { p = PlanOps.renameRoom(p, it.id, names(key)) }
            }
            return p
        }
    }

    fun build(key: String, name: String, names: (RoomKey) -> String): Project? = when (key) {
        "studio" -> Builder(700.0, 500.0).run {
            val bathV = wall(220.0, 280.0, 220.0, 500.0)
            wall(0.0, 280.0, 220.0, 280.0)
            door(bottom, 300.0, 100.0)
            door(bathV, 70.0, 75.0)
            window(top, 350.0, 200.0)
            window(right, 250.0, 150.0)
            room(110.0, 390.0, RoomKey.BATHROOM)
            room(450.0, 250.0, RoomKey.LIVING)
            build(name, names)
        }
        "one_bed" -> Builder(1000.0, 600.0).run {
            val split = wall(600.0, 0.0, 600.0, 600.0)
            val bedBath = wall(600.0, 380.0, 1000.0, 380.0)
            wall(800.0, 380.0, 800.0, 600.0)
            wall(0.0, 280.0, 300.0, 280.0)
            val kitchen = wall(300.0, 0.0, 300.0, 280.0)
            door(bottom, 550.0, 100.0)
            door(split, 300.0, 90.0, side = -1)
            door(split, 490.0, 75.0, side = -1)
            door(bedBath, 300.0, 80.0, side = -1)
            door(kitchen, 190.0, 90.0)
            window(top, 150.0, 120.0)
            window(top, 450.0, 160.0)
            window(top, 800.0, 160.0)
            window(left, 170.0, 150.0)
            room(150.0, 140.0, RoomKey.KITCHEN)
            room(400.0, 450.0, RoomKey.LIVING)
            room(800.0, 190.0, RoomKey.BEDROOM)
            room(700.0, 490.0, RoomKey.BATHROOM)
            room(900.0, 490.0, RoomKey.STORAGE)
            build(name, names)
        }
        "two_bed" -> Builder(1200.0, 750.0).run {
            val split = wall(750.0, 0.0, 750.0, 750.0)
            wall(750.0, 400.0, 1200.0, 400.0)
            wall(0.0, 300.0, 320.0, 300.0)
            val kitchen = wall(320.0, 0.0, 320.0, 300.0)
            val bath = wall(250.0, 520.0, 250.0, 750.0)
            wall(0.0, 520.0, 250.0, 520.0)
            door(bottom, 700.0, 100.0)
            door(split, 320.0, 90.0, side = -1)
            door(split, 480.0, 90.0, side = -1)
            door(kitchen, 220.0, 90.0)
            door(bath, 70.0, 75.0)
            window(top, 160.0, 120.0)
            window(top, 540.0, 180.0)
            window(top, 975.0, 160.0)
            window(right, 575.0, 150.0)
            window(left, 340.0, 150.0)
            room(160.0, 150.0, RoomKey.KITCHEN)
            room(500.0, 450.0, RoomKey.LIVING)
            room(975.0, 200.0, RoomKey.MASTER_BEDROOM)
            room(975.0, 575.0, RoomKey.BEDROOM)
            room(125.0, 635.0, RoomKey.BATHROOM)
            build(name, names)
        }
        "three_bed" -> Builder(1400.0, 900.0).run {
            val split = wall(950.0, 0.0, 950.0, 900.0)
            wall(950.0, 450.0, 1400.0, 450.0)
            val bed3 = wall(0.0, 380.0, 400.0, 380.0)
            wall(400.0, 0.0, 400.0, 380.0)
            wall(400.0, 320.0, 700.0, 320.0)
            val kitchen = wall(700.0, 0.0, 700.0, 320.0)
            val bath = wall(0.0, 680.0, 250.0, 680.0)
            wall(250.0, 680.0, 250.0, 900.0)
            val wc = wall(400.0, 680.0, 400.0, 900.0)
            wall(250.0, 680.0, 400.0, 680.0)
            door(bottom, 700.0, 100.0)
            door(split, 380.0, 90.0, side = -1)
            door(split, 520.0, 90.0, side = -1)
            door(bed3, 300.0, 90.0)
            door(kitchen, 220.0, 90.0)
            door(bath, 150.0, 75.0)
            door(wc, 110.0, 70.0)
            window(top, 200.0, 150.0)
            window(top, 550.0, 120.0)
            window(top, 825.0, 150.0)
            window(top, 1175.0, 160.0)
            window(right, 675.0, 160.0)
            window(left, 370.0, 160.0)
            room(200.0, 190.0, RoomKey.BEDROOM)
            room(550.0, 160.0, RoomKey.KITCHEN)
            room(650.0, 550.0, RoomKey.LIVING)
            room(1175.0, 225.0, RoomKey.MASTER_BEDROOM)
            room(1175.0, 675.0, RoomKey.BEDROOM)
            room(125.0, 790.0, RoomKey.BATHROOM)
            room(325.0, 790.0, RoomKey.WC)
            build(name, names)
        }
        else -> null
    }
}
