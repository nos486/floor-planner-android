package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Project

/** Groups of plan content that can be hidden together. Walls, doors, windows and rooms are always shown. */
enum class Layer { FURNITURE, ELECTRICAL, PLUMBING, DIMENSIONS, LABELS }

object Layers {
    fun of(item: Item): Layer = when (Furniture.type(item.type)?.category) {
        FurnitureCategory.ELECTRICAL -> Layer.ELECTRICAL
        FurnitureCategory.PLUMBING -> Layer.PLUMBING
        else -> Layer.FURNITURE
    }

    /** [p] without the content of the [hidden] layers, for drawing, tapping and exporting. */
    fun visible(p: Project, hidden: Set<Layer>): Project {
        if (hidden.isEmpty()) return p
        return p.copy(
            items = p.items.filter { of(it) !in hidden },
            dimensions = if (Layer.DIMENSIONS in hidden) emptyList() else p.dimensions,
            labels = if (Layer.LABELS in hidden) emptyList() else p.labels,
        )
    }
}
