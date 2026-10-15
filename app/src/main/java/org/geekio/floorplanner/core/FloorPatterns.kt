package org.geekio.floorplanner.core

import kotlin.math.floor

/** Floor materials drawn as line patterns inside a room, in world units (cm). */
object FloorPatterns {
    val materials = listOf("wood", "tile", "mosaic", "stone", "carpet")

    /** Default colours ("#rrggbb") for the room fill, cycled for rooms without their own colour. */
    val palette = listOf("#4f8cff", "#36b37e", "#ffab00", "#ff5630", "#6554c0", "#00b8d9")

    /** Colours offered for rooms. */
    val swatches = listOf("#e8d5b7", "#c8a27a", "#d9d9d9", "#a3b8c8", "#b5d3b0", "#f2c6c2", "#d6c8ef", "#fff1b8")

    fun colorFor(roomColor: String?, index: Int): String = roomColor ?: palette[index % palette.size]

    /** "#rrggbb" to 0xAARRGGBB with full alpha; null when it isn't a valid colour. */
    fun parseColor(hex: String?): Long? {
        val h = hex?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { 0xFF000000L or it }
    }

    /**
     * Pattern lines of [material] covering the box [min]-[max]; the caller clips them to the room.
     * Empty for an unknown material.
     */
    fun lines(material: String?, min: P, max: P): List<Pair<P, P>> {
        val out = mutableListOf<Pair<P, P>>()
        fun horizontal(step: Double) {
            var y = floor(min.y / step) * step
            while (y <= max.y) { out += P(min.x, y) to P(max.x, y); y += step }
        }
        fun vertical(step: Double, from: Double = min.y, to: Double = max.y, shift: Double = 0.0) {
            var x = floor((min.x - shift) / step) * step + shift
            while (x <= max.x) { out += P(x, from) to P(x, to); x += step }
        }
        /** Lines at 45° (dir = 1) or -45° (dir = -1), [step] apart horizontally. */
        fun diagonal(step: Double, dir: Int) {
            val h = max.y - min.y
            var x = floor((min.x - h) / step) * step
            while (x <= max.x + h) {
                out += if (dir > 0) P(x, min.y) to P(x + h, max.y) else P(x + h, min.y) to P(x, max.y)
                x += step
            }
        }
        when (material) {
            "wood" -> {
                // 15 cm planks with staggered joints every 120 cm.
                val step = 15.0
                horizontal(step)
                var row = floor(min.y / step).toInt()
                var y = row * step
                while (y <= max.y) {
                    vertical(120.0, y, y + step, shift = if (row % 2 == 0) 0.0 else 60.0)
                    y += step
                    row++
                }
            }
            "tile" -> { horizontal(60.0); vertical(60.0) }
            "mosaic" -> { horizontal(20.0); vertical(20.0) }
            "stone" -> { diagonal(60.0, 1); diagonal(60.0, -1) }
            "carpet" -> diagonal(12.0, 1)
        }
        return out
    }

    /** Smallest gap between the lines of [material]; patterns are hidden when this gets too small on screen. */
    fun spacing(material: String?): Double = when (material) {
        "wood" -> 15.0
        "mosaic" -> 20.0
        "carpet" -> 12.0
        "tile", "stone" -> 42.0
        else -> 0.0
    }
}
