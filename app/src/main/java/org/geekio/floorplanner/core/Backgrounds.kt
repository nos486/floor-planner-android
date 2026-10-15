package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Background

object Backgrounds {
    /** A background [width] × [height] pixels, about [span] world units wide, centred on [center]. */
    fun place(width: Int, height: Int, center: P, span: Double): Background {
        val scale = span / maxOf(1, width)
        return Background(center.x - width * scale / 2, center.y - height * scale / 2, scale)
    }

    /**
     * Rescales [bg] so that the line [a]-[b] drawn over it is [length] world units long. Point [a] stays where
     * it is on the image, so the drawing over it lines up.
     */
    fun calibrate(bg: Background, a: P, b: P, length: Double): Background {
        val d = a.dist(b)
        if (d < 1e-6 || length <= 0) return bg
        val k = length / d
        return bg.copy(x = a.x + (bg.x - a.x) * k, y = a.y + (bg.y - a.y) * k, scale = bg.scale * k)
    }

    fun move(bg: Background, delta: P) = bg.copy(x = bg.x + delta.x, y = bg.y + delta.y)
}
