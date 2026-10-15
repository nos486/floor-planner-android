package org.geekio.floorplanner.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Furniture
import org.geekio.floorplanner.core.FurnitureCategory
import org.geekio.floorplanner.core.Prim
import org.geekio.floorplanner.model.Item

/** Draws the furniture symbol of [item] in world units (the caller sets up the world transform). */
fun DrawScope.drawItem(item: Item, fill: Color, stroke: Color, strokeWidth: Float) {
    translate(item.x.toFloat(), item.y.toFloat()) {
        rotate(item.rotation.toFloat(), pivot = Offset.Zero) {
            drawPrims(Furniture.shapes(item), fill, stroke, strokeWidth)
        }
    }
}

fun DrawScope.drawPrims(prims: List<Prim>, fill: Color, stroke: Color, strokeWidth: Float) {
    val st = Stroke(strokeWidth)
    for (p in prims) when (p) {
        is Prim.Rect -> {
            val tl = Offset(p.x.toFloat(), p.y.toFloat())
            val size = Size(p.w.toFloat(), p.h.toFloat())
            val cr = CornerRadius(p.r.toFloat())
            if (p.fill) drawRoundRect(fill, tl, size, cr)
            drawRoundRect(stroke, tl, size, cr, style = st)
        }
        is Prim.Circle -> {
            val c = Offset(p.cx.toFloat(), p.cy.toFloat())
            if (p.fill) drawCircle(fill, p.r.toFloat(), c)
            drawCircle(stroke, p.r.toFloat(), c, style = st)
        }
        is Prim.Line -> drawLine(
            stroke, Offset(p.x1.toFloat(), p.y1.toFloat()), Offset(p.x2.toFloat(), p.y2.toFloat()),
            strokeWidth, cap = StrokeCap.Round,
        )
    }
}

fun furnitureName(key: String): Int = when (key) {
    "sofa3" -> R.string.f_sofa3
    "sofa2" -> R.string.f_sofa2
    "armchair" -> R.string.f_armchair
    "coffee_table" -> R.string.f_coffee_table
    "tv_stand" -> R.string.f_tv_stand
    "dining4" -> R.string.f_dining4
    "dining6" -> R.string.f_dining6
    "round_table" -> R.string.f_round_table
    "bookshelf" -> R.string.f_bookshelf
    "plant" -> R.string.f_plant
    "bed_double" -> R.string.f_bed_double
    "bed_single" -> R.string.f_bed_single
    "nightstand" -> R.string.f_nightstand
    "wardrobe" -> R.string.f_wardrobe
    "desk" -> R.string.f_desk
    "fridge" -> R.string.f_fridge
    "stove" -> R.string.f_stove
    "kitchen_sink" -> R.string.f_kitchen_sink
    "counter" -> R.string.f_counter
    "washer" -> R.string.f_washer
    "toilet" -> R.string.f_toilet
    "squat_toilet" -> R.string.f_squat_toilet
    "bathtub" -> R.string.f_bathtub
    "shower" -> R.string.f_shower
    "basin" -> R.string.f_basin
    "socket" -> R.string.f_socket
    "switch" -> R.string.f_switch
    "ceiling_light" -> R.string.f_ceiling_light
    "wall_light" -> R.string.f_wall_light
    "power_panel" -> R.string.f_power_panel
    "ac_unit" -> R.string.f_ac_unit
    "water_point" -> R.string.f_water_point
    "drain" -> R.string.f_drain
    "gas_valve" -> R.string.f_gas_valve
    "water_heater" -> R.string.f_water_heater
    "boiler" -> R.string.f_boiler
    "radiator" -> R.string.f_radiator
    else -> R.string.f_item
}

fun categoryName(c: FurnitureCategory): Int = when (c) {
    FurnitureCategory.LIVING -> R.string.cat_living
    FurnitureCategory.BEDROOM -> R.string.cat_bedroom
    FurnitureCategory.KITCHEN -> R.string.cat_kitchen
    FurnitureCategory.BATHROOM -> R.string.cat_bathroom
    FurnitureCategory.ELECTRICAL -> R.string.cat_electrical
    FurnitureCategory.PLUMBING -> R.string.cat_plumbing
}
