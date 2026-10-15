package org.geekio.floorplanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.ProjectIO
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.model.Project
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Renders a plan to PNG and PDF with the same drawing code as the screen. */
object PlanExport {
    private const val PNG_LONG_EDGE = 2400f

    /** Plan extents in world units, padded so the overall dimensions fit. */
    private fun bounds(p: Project): Pair<P, P> {
        val pts = ProjectIO.extentPoints(p).ifEmpty { listOf(P(0.0, 0.0), P(500.0, 500.0)) }
        return P(pts.minOf { it.x }, pts.minOf { it.y }) to P(pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    /** A view that fits [p] into [w] x [h] pixels, keeping [margin] pixels free on every side. */
    fun fit(p: Project, settings: Settings, w: Float, h: Float, margin: Float, left: Float = 0f, top: Float = 0f): PlanView {
        val (min, max) = bounds(p)
        val bw = maxOf(max.x - min.x, 50.0)
        val bh = maxOf(max.y - min.y, 50.0)
        val zoom = minOf((w - 2 * margin) / bw, (h - 2 * margin) / bh).toFloat()
        val cx = ((min.x + max.x) / 2).toFloat()
        val cy = ((min.y + max.y) / 2).toFloat()
        return PlanView(p, Offset(left + w / 2 - cx * zoom, top + h / 2 - cy * zoom), zoom, settings.copy(showGrid = false))
    }

    private fun measurer(context: Context, density: Float) =
        TextMeasurer(createFontFamilyResolver(context), Density(density), LayoutDirection.Ltr)

    private fun draw(canvas: Canvas, density: Float, size: Size, block: DrawScope.() -> Unit) {
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, canvas, size, block)
    }

    fun png(context: Context, p: Project, settings: Settings): ByteArray {
        val (min, max) = bounds(p)
        val aspect = ((max.x - min.x + 200) / (max.y - min.y + 200)).toFloat().coerceIn(0.4f, 2.5f)
        val w = if (aspect >= 1) PNG_LONG_EDGE else PNG_LONG_EDGE * aspect
        val h = if (aspect >= 1) PNG_LONG_EDGE / aspect else PNG_LONG_EDGE
        val density = PNG_LONG_EDGE / 900f
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val tm = measurer(context, density)
        draw(Canvas(bitmap.asImageBitmap()), density, Size(w, h)) {
            drawPlan(fit(p, settings, w, h, 80 * density), PlanColors.print, tm)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /** One A4 page: title, the plan to scale, a room schedule and a scale bar. */
    fun pdf(context: Context, p: Project, settings: Settings): ByteArray {
        val (min, max) = bounds(p)
        val landscape = (max.x - min.x) > (max.y - min.y)
        val pw = if (landscape) 842 else 595
        val ph = if (landscape) 595 else 842
        val density = 0.75f
        val tm = measurer(context, density)
        val u = settings.units
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
        val margin = 36f

        val cols = if (landscape) 4 else 3
        val rows = ceil((p.rooms.size + 1) / cols.toDouble()).toInt().coerceAtLeast(1)
        val tableH = 22f + rows * 14f
        val headerH = 46f
        val planTop = margin + headerH
        val planH = ph - planTop - margin - tableH - 16f
        val planW = pw - 2 * margin
        val view = fit(p, settings, planW, planH, 40f, left = margin, top = planTop)

        draw(Canvas(page.canvas), density, Size(pw.toFloat(), ph.toFloat())) {
            drawRect(Color.White)
            drawPlan(view, PlanColors.print, tm)

            val dark = Color(0xFF1F2937)
            val grey = Color(0xFF6B7280)
            text(tm, p.projectName, Offset(margin, margin), 18.sp, dark, FontWeight.Bold, planW)
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
            text(
                tm, date + "  ·  " + context.getString(R.string.total_area, Units.area(p.totalAreaM2, u), p.rooms.size),
                Offset(margin, margin + 24f), 10.sp, grey, FontWeight.Normal, planW,
            )
            drawLine(Color(0xFFD1D5DB), Offset(margin, planTop - 6f), Offset(pw - margin, planTop - 6f), 0.8f)

            // Room schedule
            val tableTop = ph - margin - tableH
            drawLine(Color(0xFFD1D5DB), Offset(margin, tableTop), Offset(pw - margin, tableTop), 0.8f)
            val colW = planW / cols
            val cells = p.rooms.map { it.name to Units.area(it.areaM2, u) } +
                (context.getString(R.string.total) to Units.area(p.totalAreaM2, u))
            cells.forEachIndexed { i, (name, area) ->
                val x = margin + (i / rows) * colW
                val y = tableTop + 8f + (i % rows) * 14f
                val bold = i == cells.lastIndex
                text(tm, name, Offset(x, y), 9.sp, dark, if (bold) FontWeight.Bold else FontWeight.Normal, colW * 0.6f)
                text(tm, area, Offset(x + colW * 0.6f, y), 9.sp, dark, if (bold) FontWeight.Bold else FontWeight.Normal, colW * 0.4f - 8f)
            }

            // Scale bar: the longest round length that fits in 120 pt.
            val upm = p.scale.unitsPerMeter
            val steps = if (u == UnitSystem.IMPERIAL) listOf(30.0, 20.0, 10.0, 5.0, 2.0).map { it * 0.3048 } else listOf(10.0, 5.0, 2.0, 1.0, 0.5)
            val meters = steps.firstOrNull { it * upm * view.zoom <= 120 } ?: steps.last()
            val barW = (meters * upm * view.zoom).toFloat()
            val barY = planTop + planH + 6f
            val barX = pw - margin - barW
            drawLine(dark, Offset(barX, barY), Offset(barX + barW, barY), 1.5f)
            drawLine(dark, Offset(barX, barY - 3f), Offset(barX, barY + 3f), 1f)
            drawLine(dark, Offset(barX + barW, barY - 3f), Offset(barX + barW, barY + 3f), 1f)
            // Real cm per world unit divided by paper cm per world unit (1 pt = 2.54 / 72 cm).
            val ratio = (100.0 / upm) / (view.zoom * 2.54 / 72.0)
            text(
                tm, Units.length(meters, u) + String.format(Locale.getDefault(), "  ·  1:%d", Math.round(ratio)),
                Offset(barX - 110f, barY - 6f), 8.sp, grey, FontWeight.Normal, 104f,
            )
        }
        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    private fun DrawScope.text(
        tm: TextMeasurer, s: String, at: Offset, size: androidx.compose.ui.unit.TextUnit, color: Color, weight: FontWeight, maxWidth: Float,
    ) {
        val layout = tm.measure(
            s, TextStyle(fontSize = size, color = color, fontWeight = weight),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxOf(1, maxWidth.toInt())),
        )
        drawText(layout, topLeft = at)
    }
}
