package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Floor
import org.geekio.floorplanner.model.Project

/** Storeys of a plan. The open floor's content lives in the top-level project fields, see [Project.floors]. */
object Floors {
    fun count(p: Project) = maxOf(1, p.floors.size)

    /** The open floor with its content. [name] is used while the plan has a single floor. */
    fun current(p: Project, name: String = ""): Floor {
        val stub = p.floors.getOrNull(p.floorIndex)
        return Floor(
            id = stub?.id ?: "floor_1", name = stub?.name ?: name,
            walls = p.walls, doors = p.doors, windows = p.windows, rooms = p.rooms,
            items = p.items, dimensions = p.dimensions, labels = p.labels,
        )
    }

    /** Every floor with its content, bottom first. */
    fun all(p: Project, firstName: String = ""): List<Floor> =
        if (p.floors.isEmpty()) listOf(current(p, firstName))
        else p.floors.mapIndexed { i, f -> if (i == p.floorIndex) current(p) else f }

    private fun Floor.stub() = Floor(id, name)

    /** Opens floor [index] of [list]. */
    private fun load(p: Project, list: List<Floor>, index: Int): Project {
        val f = list[index]
        return p.copy(
            walls = f.walls, doors = f.doors, windows = f.windows, rooms = f.rooms,
            items = f.items, dimensions = f.dimensions, labels = f.labels,
            floors = list.mapIndexed { i, fl -> if (i == index) fl.stub() else fl },
            floorIndex = index,
        )
    }

    fun switchTo(p: Project, index: Int): Project {
        if (index == p.floorIndex || index !in 0 until count(p) || p.floors.isEmpty()) return p
        return load(p, all(p), index)
    }

    /** Adds an empty floor on top and opens it. [firstName] names the existing floor of a single-floor plan. */
    fun add(p: Project, name: String, firstName: String): Project {
        val list = all(p, firstName) + Floor(generateId("fl"), name)
        return load(p, list, list.lastIndex)
    }

    /** Removes floor [index] (never the last one left) and opens the floor below it, or the new bottom floor. */
    fun delete(p: Project, index: Int): Project {
        if (p.floors.size < 2 || index !in p.floors.indices) return p
        val list = all(p).filterIndexed { i, _ -> i != index }
        val open = when {
            index < p.floorIndex -> p.floorIndex - 1
            index == p.floorIndex -> maxOf(0, index - 1)
            else -> p.floorIndex
        }
        return load(p, list, open)
    }

    fun rename(p: Project, index: Int, name: String): Project {
        val n = name.trim()
        if (n.isEmpty() || index !in p.floors.indices) return p
        return p.copy(floors = p.floors.mapIndexed { i, f -> if (i == index) f.copy(name = n) else f })
    }

    /** The floor under the open one, drawn faintly to line walls up. */
    fun below(p: Project): Floor? = if (p.floors.isEmpty() || p.floorIndex == 0) null else p.floors.getOrNull(p.floorIndex - 1)

    fun totalAreaM2(p: Project): Double = all(p).sumOf { f -> f.rooms.sumOf { it.areaM2 } }
}
