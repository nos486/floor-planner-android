package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Dimension
import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Label
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall

/** Texts of the sample plan that aren't room names. */
enum class SampleText { NAME, GROUND_FLOOR, FIRST_FLOOR, ENTRANCE }

/**
 * A finished two-storey home that shows what the app can do: furnished rooms, floor colours and
 * materials, electrical and plumbing points, stairs, measurements and notes.
 */
object Samples {
    private const val W = 1200.0
    private const val H = 900.0
    private const val OUTER = 20.0
    private const val INNER = 12.0

    private class Storey {
        val walls = mutableListOf<Wall>()
        val doors = mutableListOf<Door>()
        val windows = mutableListOf<PlanWindow>()
        val items = mutableListOf<Item>()
        val labels = mutableListOf<Label>()
        val dimensions = mutableListOf<Dimension>()
        val rooms = mutableListOf<Triple<P, RoomKey, Pair<String, String>>>()

        val top = wall(0.0, 0.0, W, 0.0, OUTER)
        val right = wall(W, 0.0, W, H, OUTER)
        val bottom = wall(W, H, 0.0, H, OUTER)
        val left = wall(0.0, H, 0.0, 0.0, OUTER)

        fun wall(x1: Double, y1: Double, x2: Double, y2: Double, t: Double = INNER): String {
            val id = "w${walls.size + 1}"
            walls += Wall.create(id, P(x1, y1), P(x2, y2), t)
            return id
        }

        fun door(wall: String, at: Double, width: Double = 90.0, side: Int = 1, style: String? = null) {
            doors += Door("d${doors.size + 1}", wall, at, width, side = side, style = style)
        }

        fun window(wall: String, at: Double, width: Double) {
            windows += PlanWindow("win${windows.size + 1}", wall, at, width)
        }

        /** Rotation 0 has the item's back to the north (top), 90 to the east, 180 south, 270 west. */
        fun item(type: String, x: Double, y: Double, rotation: Double = 0.0) {
            val t = Furniture.type(type) ?: return
            items += Furniture.create(t, P(x, y), rotation).copy(id = "i${items.size + 1}")
        }

        fun room(x: Double, y: Double, key: RoomKey, color: String, floor: String) {
            rooms += Triple(P(x, y), key, color to floor)
        }

        fun build(base: Project, names: (RoomKey) -> String): Project {
            var p = PlanOps.withRooms(
                base.copy(
                    walls = walls, doors = doors, windows = windows, items = items, labels = labels, dimensions = dimensions,
                    rooms = emptyList(),
                ),
            )
            for ((pt, key, look) in rooms) {
                val room = p.rooms.firstOrNull { pointInPolygon(pt, it.polygon) } ?: continue
                p = PlanOps.renameRoom(p, room.id, names(key))
                p = PlanOps.setRoomColor(p, room.id, look.first)
                p = PlanOps.setRoomFloor(p, room.id, look.second)
            }
            return p
        }
    }

    fun home(names: (RoomKey) -> String, text: (SampleText) -> String): Project {
        val base = Project(projectName = text(SampleText.NAME), wallHeight = 280.0)
        val ground = groundFloor(text(SampleText.ENTRANCE)).build(base, names)
        val first = firstFloor().build(Floors.add(ground, text(SampleText.FIRST_FLOOR), text(SampleText.GROUND_FLOOR)), names)
        return Floors.switchTo(first, 0)
    }

    private fun groundFloor(entrance: String) = Storey().apply {
        wall(450.0, 0.0, 450.0, 380.0)
        val kitchenS = wall(0.0, 380.0, 450.0, 380.0)
        wall(450.0, 220.0, 650.0, 220.0)
        val wcE = wall(650.0, 0.0, 650.0, 220.0)
        wall(850.0, 0.0, 850.0, 400.0)
        val studyS = wall(850.0, 400.0, W, 400.0)

        door(bottom, 200.0, 100.0)
        door(kitchenS, 350.0, 90.0, style = Door.SLIDING)
        door(wcE, 140.0, 70.0)
        door(studyS, 120.0, 90.0, side = -1)

        window(top, 225.0, 160.0)
        window(top, 550.0, 60.0)
        window(top, 1025.0, 160.0)
        window(bottom, 550.0, 240.0)
        window(bottom, 950.0, 200.0)
        window(left, 650.0, 120.0)
        window(right, 200.0, 120.0)
        window(right, 650.0, 160.0)

        room(200.0, 200.0, RoomKey.KITCHEN, "#e8d5b7", "tile")
        room(550.0, 100.0, RoomKey.WC, "#a3b8c8", "mosaic")
        room(1025.0, 200.0, RoomKey.STUDY, "#d6c8ef", "carpet")
        room(600.0, 650.0, RoomKey.LIVING, "#c8a27a", "wood")

        // Kitchen
        item("fridge", 48.0, 46.0)
        item("counter", 110.0, 40.0)
        item("kitchen_sink", 180.0, 40.0)
        item("counter", 250.0, 40.0)
        item("stove", 310.0, 40.0)
        item("counter", 370.0, 40.0)
        item("washer", 40.0, 130.0, 270.0)
        item("water_point", 180.0, 18.0)
        item("drain", 180.0, 85.0)
        item("gas_valve", 310.0, 16.0)
        item("boiler", 425.0, 200.0, 90.0)
        item("ceiling_light", 225.0, 230.0)
        item("socket", 250.0, 17.0)
        item("socket", 17.0, 300.0, 270.0)
        item("switch", 410.0, 373.0, 180.0)
        // WC
        item("toilet", 500.0, 45.0)
        item("basin", 600.0, 35.0)
        item("water_point", 600.0, 15.0)
        item("drain", 500.0, 120.0)
        item("ceiling_light", 550.0, 150.0)
        // Stairs up, against the WC
        item("stairs", 710.0, 170.0)
        // Living and dining
        item("dining6", 230.0, 500.0)
        item("tv_stand", 33.0, 760.0, 270.0)
        item("sofa3", 400.0, 760.0, 90.0)
        item("coffee_table", 250.0, 760.0, 90.0)
        item("plant", 40.0, 600.0)
        item("fireplace", 1165.0, 490.0, 90.0)
        item("sofa2", 1000.0, 620.0, 270.0)
        item("plant", 1150.0, 850.0)
        item("ac_unit", 831.0, 300.0, 90.0)
        item("ceiling_light", 230.0, 500.0)
        item("ceiling_light", 620.0, 700.0)
        item("ceiling_light", 1000.0, 650.0)
        item("power_panel", 1110.0, 882.0, 180.0)
        item("switch", 900.0, 883.0, 180.0)
        item("socket", 17.0, 680.0, 270.0)
        item("socket", 700.0, 883.0, 180.0)
        item("radiator", 650.0, 880.0, 180.0)
        item("radiator", 1180.0, 650.0, 90.0)
        // Study
        item("desk", 1025.0, 55.0)
        item("bed_single", 1140.0, 280.0)
        item("bookshelf", 880.0, 200.0, 270.0)
        item("ceiling_light", 1025.0, 200.0)
        item("socket", 960.0, 17.0)
        item("radiator", 1180.0, 200.0, 90.0)

        labels += Label("l1", entrance, 1000.0, 990.0, 28.0, listOf(1000.0, 912.0))
    }

    private fun firstFloor() = Storey().apply {
        val hall = wall(450.0, 0.0, 450.0, H)
        wall(0.0, 450.0, 450.0, 450.0)
        val bathS = wall(450.0, 300.0, 650.0, 300.0)
        wall(650.0, 0.0, 650.0, 300.0)
        val bed2W = wall(850.0, 0.0, 850.0, 450.0)
        wall(850.0, 450.0, W, 450.0)

        door(hall, 380.0)
        door(hall, 540.0)
        door(bathS, 100.0, 75.0, side = -1)
        door(bed2W, 380.0, side = -1)

        window(left, 675.0, 140.0)
        window(left, 225.0, 160.0)
        window(top, 550.0, 60.0)
        window(right, 225.0, 140.0)
        window(right, 675.0, 160.0)
        window(bottom, 375.0, 240.0)
        window(bottom, 975.0, 160.0)

        room(200.0, 200.0, RoomKey.MASTER_BEDROOM, "#f2c6c2", "wood")
        room(200.0, 700.0, RoomKey.BEDROOM, "#b5d3b0", "wood")
        room(550.0, 150.0, RoomKey.BATHROOM, "#a3b8c8", "tile")
        room(1025.0, 200.0, RoomKey.BEDROOM, "#fff1b8", "carpet")
        room(800.0, 700.0, RoomKey.FAMILY, "#d9d9d9", "stone")

        // Stairs arrive in the hall above the ones below.
        item("stairs", 710.0, 170.0)
        // Main bedroom
        item("bed_double", 225.0, 120.0)
        item("nightstand", 115.0, 35.0)
        item("nightstand", 335.0, 35.0)
        item("wardrobe", 225.0, 412.0, 180.0)
        item("ceiling_light", 225.0, 230.0)
        item("wall_light", 115.0, 17.0)
        item("wall_light", 335.0, 17.0)
        item("socket", 85.0, 17.0)
        item("switch", 443.0, 440.0, 90.0)
        item("radiator", 20.0, 225.0, 270.0)
        // Second bedroom
        item("bed_single", 60.0, 560.0)
        item("desk", 300.0, 845.0, 180.0)
        item("wardrobe", 414.0, 700.0, 90.0)
        item("ceiling_light", 225.0, 675.0)
        item("socket", 300.0, 883.0, 180.0)
        item("radiator", 20.0, 675.0, 270.0)
        // Bathroom
        item("bathtub", 550.0, 50.0)
        item("toilet", 489.0, 160.0, 270.0)
        item("basin", 620.0, 160.0, 90.0)
        item("water_point", 540.0, 15.0)
        item("water_point", 638.0, 160.0, 90.0)
        item("drain", 550.0, 120.0)
        item("water_heater", 610.0, 255.0)
        item("ceiling_light", 550.0, 150.0)
        // Third bedroom
        item("bed_double", 1025.0, 120.0)
        item("nightstand", 895.0, 35.0)
        item("nightstand", 1155.0, 35.0)
        item("wardrobe", 1060.0, 412.0, 180.0)
        item("ceiling_light", 1025.0, 225.0)
        item("ac_unit", 1180.0, 225.0, 90.0)
        // Family room
        item("tv_stand", 1000.0, 482.0)
        item("sofa3", 1000.0, 800.0, 180.0)
        item("coffee_table", 1000.0, 690.0)
        item("armchair", 800.0, 690.0, 270.0)
        item("bookshelf", 560.0, 870.0, 180.0)
        item("plant", 480.0, 340.0)
        item("ceiling_light", 900.0, 680.0)
        item("socket", 940.0, 463.0)
        item("radiator", 825.0, 880.0, 180.0)
        // Width of the family room, wall face to wall face
        dimensions += Dimension("dm1", listOf(listOf(456.0, 600.0), listOf(1190.0, 600.0)), 0.0)
    }
}
