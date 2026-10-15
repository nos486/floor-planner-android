package org.geekio.floorplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.geekio.floorplanner.core.DimensionGeometry
import org.geekio.floorplanner.core.Dimensions
import org.geekio.floorplanner.core.Furniture
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.PlanOps
import org.geekio.floorplanner.core.SnapType
import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.PlanWindow
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor

private fun P.o() = Offset(x.toFloat(), y.toFloat())

private val roomPalette = listOf(
    Color(0xFF4F8CFF), Color(0xFF36B37E), Color(0xFFFFAB00),
    Color(0xFFFF5630), Color(0xFF6554C0), Color(0xFF00B8D9),
)

private data class PlanColors(
    val background: Color,
    val gridMinor: Color,
    val gridMajor: Color,
    val wall: Color,
    val selected: Color,
    val window: Color,
    val label: Color,
    val labelBg: Color,
    val dim: Color,
    val itemFill: Color,
    val itemStroke: Color,
)

@Composable
fun PlanCanvas(vm: PlanViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val colors = PlanColors(
        background = cs.surface,
        gridMinor = cs.outlineVariant.copy(alpha = 0.35f),
        gridMajor = cs.outlineVariant.copy(alpha = 0.9f),
        wall = cs.onSurface.copy(alpha = 0.85f),
        selected = cs.primary,
        window = Color(0xFF2B7BD6),
        label = cs.onSurface,
        labelBg = cs.surface.copy(alpha = 0.8f),
        dim = cs.onSurfaceVariant,
        itemFill = cs.surfaceContainerHighest,
        itemStroke = cs.onSurface.copy(alpha = 0.7f),
    )
    val textMeasurer = rememberTextMeasurer()
    vm.density = LocalDensity.current.density

    Canvas(
        modifier
            .onSizeChanged { vm.onViewSize(it) }
            .pointerInput(vm) { planGestures(vm) }
            .pointerInput(vm) { wheelZoom(vm) }
    ) {
        drawRect(colors.background)
        if (vm.settings.showGrid) drawGrid(vm, colors)

        val project = vm.project
        val zoom = vm.zoom
        val sel = vm.selection
        val px = 1f / zoom // one screen pixel in world units

        withTransform({
            translate(vm.pan.x, vm.pan.y)
            scale(zoom, zoom, pivot = Offset.Zero)
        }) {
            // Rooms
            project.rooms.forEachIndexed { i, room ->
                val poly = room.polygon
                if (poly.size < 3) return@forEachIndexed
                val path = Path().apply {
                    moveTo(poly[0].x.toFloat(), poly[0].y.toFloat())
                    poly.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
                    close()
                }
                val isSel = sel?.kind == Kind.ROOM && sel.id == room.id
                drawPath(path, roomPalette[i % roomPalette.size].copy(alpha = if (isSel) 0.35f else 0.15f))
            }

            // Furniture
            for (item in project.items) {
                val isSel = sel?.kind == Kind.ITEM && sel.id == item.id
                drawItem(
                    item,
                    if (isSel) colors.selected.copy(alpha = 0.18f) else colors.itemFill,
                    if (isSel) colors.selected else colors.itemStroke,
                    maxOf(1.2f, 1.5f * px),
                )
            }

            // Walls
            for (wall in project.walls) {
                val isSel = sel?.kind == Kind.WALL && sel.id == wall.id
                drawLine(
                    if (isSel) colors.selected else colors.wall,
                    wall.a.o(), wall.b.o(),
                    strokeWidth = wall.thickness.toFloat(),
                    cap = StrokeCap.Round,
                )
            }

            // Openings
            for (door in project.doors) {
                drawDoor(vm, door, sel?.kind == Kind.DOOR && sel.id == door.id, colors, px, ghost = false)
            }
            for (win in project.windows) {
                drawWindow(vm, win, sel?.kind == Kind.WINDOW && sel.id == win.id, colors, px, ghost = false)
            }

            // Draft
            when (val d = vm.draft) {
                is Draft.WallDraft -> drawLine(
                    colors.selected.copy(alpha = 0.6f), d.a.o(), d.b.o(),
                    strokeWidth = vm.settings.wallThickness.toFloat(), cap = StrokeCap.Round,
                )
                is Draft.OpeningDraft -> {
                    if (d.isDoor) drawDoor(vm, Door("draft", d.wallId, d.offset, d.width), true, colors, px, ghost = true)
                    else drawWindow(vm, PlanWindow("draft", d.wallId, d.offset, d.width), true, colors, px, ghost = true)
                }
                // Drawn on top in screen space below.
                is Draft.MeasureDraft, null -> Unit
            }
        }

        // ----- Screen-space overlays -----
        drawDimensions(vm, textMeasurer, colors)
        if (vm.settings.showOverall) {
            for (g in Dimensions.overall(project, (34.dp.toPx() / zoom).toDouble())) {
                drawDimensionLine(vm, g, textMeasurer, colors.dim, colors)
            }
        }
        for (d in project.dimensions) {
            val g = Dimensions.geometry(d) ?: continue
            val isSel = sel?.kind == Kind.DIMENSION && sel.id == d.id
            drawDimensionLine(vm, g, textMeasurer, if (isSel) colors.selected else colors.label, colors)
        }
        (vm.draft as? Draft.MeasureDraft)?.let { d ->
            Dimensions.geometry(d.a, d.b, 0.0)?.let { drawDimensionLine(vm, it, textMeasurer, colors.selected, colors) }
            drawCircle(colors.selected, 5.dp.toPx(), vm.toScreen(d.a))
            if (d.snap == SnapType.ENDPOINT) {
                drawCircle(colors.selected, 12.dp.toPx(), vm.toScreen(d.b), style = Stroke(2.dp.toPx()))
            }
            drawCircle(colors.selected, 5.dp.toPx(), vm.toScreen(d.b))
        }
        drawRoomLabels(vm, textMeasurer, colors)

        (vm.draft as? Draft.WallDraft)?.let { d ->
            val upm = project.scale.unitsPerMeter
            drawTag(textMeasurer, String.format(Locale.getDefault(), "%.2f m", d.a.dist(d.b) / upm),
                vm.toScreen(d.b) + Offset(0f, -36.dp.toPx()), colors.selected, Color.White)
            drawCircle(colors.selected, 6.dp.toPx(), vm.toScreen(d.a))
            if (d.snap == SnapType.ENDPOINT) {
                drawCircle(colors.selected, 12.dp.toPx(), vm.toScreen(d.b), style = Stroke(2.dp.toPx()))
            }
            drawCircle(colors.selected, 6.dp.toPx(), vm.toScreen(d.b))
        }

        // Outline and rotation handle of the selected item
        if (sel?.kind == Kind.ITEM) {
            project.item(sel.id)?.let { item ->
                val corners = Furniture.corners(item).map { vm.toScreen(it) }
                val outline = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(outline, colors.selected, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                vm.itemRotateHandle(item.id)?.let { h ->
                    val front = vm.toScreen(Furniture.toWorld(item, P(0.0, item.depth / 2)))
                    drawLine(colors.selected, front, h, 2.dp.toPx())
                    drawCircle(colors.background, 11.dp.toPx(), h)
                    drawCircle(colors.selected, 11.dp.toPx(), h, style = Stroke(3.dp.toPx()))
                    drawArc(
                        colors.selected, -60f, 240f, useCenter = false,
                        topLeft = h - Offset(5.dp.toPx(), 5.dp.toPx()), size = Size(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }

        // Endpoint handles of the selected wall
        if (sel?.kind == Kind.WALL) {
            project.wall(sel.id)?.let { wall ->
                for (pt in listOf(wall.a, wall.b)) {
                    val s = vm.toScreen(pt)
                    drawCircle(colors.background, 10.dp.toPx(), s)
                    drawCircle(colors.selected, 10.dp.toPx(), s, style = Stroke(3.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawDoor(vm: PlanViewModel, door: Door, selected: Boolean, c: PlanColors, px: Float, ghost: Boolean) {
    val wall = vm.project.wall(door.wallId) ?: return
    val g = PlanOps.openingGeometry(wall, door) ?: return
    val s = PlanOps.doorSwing(g, door)
    val color = if (selected) c.selected else c.wall
    drawLine(if (ghost) c.selected.copy(alpha = 0.4f) else c.background, g.start.o(), g.end.o(), (wall.thickness + 2).toFloat())
    drawLine(color, s.hinge.o(), s.leafEnd.o(), strokeWidth = maxOf(3f, 2 * px))
    val r = s.radius.toFloat()
    val along = s.strike - s.hinge
    val start = Math.toDegrees(atan2(along.y, along.x)).toFloat()
    drawArc(
        color, start, if (s.clockwise) 90f else -90f, useCenter = false,
        topLeft = s.hinge.o() - Offset(r, r), size = Size(2 * r, 2 * r),
        style = Stroke(width = 1.5f * px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * px, 4 * px))),
    )
}

private fun DrawScope.drawWindow(vm: PlanViewModel, win: PlanWindow, selected: Boolean, c: PlanColors, px: Float, ghost: Boolean) {
    val wall = vm.project.wall(win.wallId) ?: return
    val g = PlanOps.openingGeometry(wall, win) ?: return
    drawLine(if (ghost) c.selected.copy(alpha = 0.4f) else c.background, g.start.o(), g.end.o(), (wall.thickness + 2).toFloat())
    val color = if (selected) c.selected else c.window
    for (k in listOf(-0.3, 0.3)) {
        val off = g.normal * (wall.thickness / 2 * k)
        drawLine(color, (g.start + off).o(), (g.end + off).o(), strokeWidth = maxOf(2f, 1.5f * px))
    }
    for (pt in listOf(g.start, g.end)) {
        val n = g.normal * (wall.thickness / 2)
        drawLine(color, (pt - n).o(), (pt + n).o(), strokeWidth = maxOf(2f, 1.5f * px))
    }
}

private fun DrawScope.drawGrid(vm: PlanViewModel, c: PlanColors) {
    val scale = vm.project.scale
    val grid = scale.gridCm * scale.unitsPerMeter / 100
    val meter = scale.unitsPerMeter
    val zoom = vm.zoom
    val tl = vm.toWorld(Offset.Zero)
    val br = vm.toWorld(Offset(size.width, size.height))

    fun lines(step: Double, color: Color, width: Float) {
        if (step * zoom < 6) return
        var x = floor(tl.x / step) * step
        val endX = ceil(br.x / step) * step
        while (x <= endX) {
            val sx = (x * zoom).toFloat() + vm.pan.x
            drawLine(color, Offset(sx, 0f), Offset(sx, size.height), width)
            x += step
        }
        var y = floor(tl.y / step) * step
        val endY = ceil(br.y / step) * step
        while (y <= endY) {
            val sy = (y * zoom).toFloat() + vm.pan.y
            drawLine(color, Offset(0f, sy), Offset(size.width, sy), width)
            y += step
        }
    }
    lines(grid, c.gridMinor, 1f)
    lines(meter, c.gridMajor, 1.5f)
}

private fun DrawScope.drawDimensions(vm: PlanViewModel, tm: TextMeasurer, c: PlanColors) {
    val upm = vm.project.scale.unitsPerMeter
    val style = TextStyle(fontSize = 11.sp, color = c.dim)
    for (wall in vm.project.walls) {
        val a = vm.toScreen(wall.a)
        val b = vm.toScreen(wall.b)
        if ((a - b).getDistance() < 70.dp.toPx()) continue
        val dir = (wall.b - wall.a).normalized()
        val mid = (wall.a + wall.b) * 0.5
        val offWorld = mid + dir.perp() * (wall.thickness / 2)
        val screenOff = vm.toScreen(offWorld) + Offset(-dir.y.toFloat(), dir.x.toFloat()) * 12.dp.toPx()
        val layout = tm.measure(String.format(Locale.getDefault(), "%.2f", wall.length / upm), style)
        drawText(layout, topLeft = screenOff - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** A dimension line with extension lines, slash ticks and its length, drawn in screen space. */
private fun DrawScope.drawDimensionLine(vm: PlanViewModel, g: DimensionGeometry, tm: TextMeasurer, color: Color, c: PlanColors) {
    val start = vm.toScreen(g.start)
    val end = vm.toScreen(g.end)
    val len = (end - start).getDistance()
    if (len < 8.dp.toPx()) return
    val dir = (end - start) / len
    val n = Offset(g.normal.x.toFloat(), g.normal.y.toFloat())
    val over = n * (6.dp.toPx() * g.side.toFloat())
    val w = 1.dp.toPx()
    drawLine(color.copy(alpha = 0.6f), vm.toScreen(g.fromA), start + over, w)
    drawLine(color.copy(alpha = 0.6f), vm.toScreen(g.fromB), end + over, w)
    drawLine(color, start, end, 1.3f * w)
    val tick = (dir + n) * (5.dp.toPx())
    drawLine(color, start - tick, start + tick, 1.6f * w)
    drawLine(color, end - tick, end + tick, 1.6f * w)
    val text = String.format(Locale.getDefault(), "%.2f", g.start.dist(g.end) / vm.project.scale.unitsPerMeter)
    val layout = tm.measure(text, TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium))
    val side = if (g.side == 0.0) -1f else g.side.toFloat()
    val center = (start + end) / 2f + n * (side * 10.dp.toPx())
    val tl = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    drawRoundRect(c.labelBg, tl - Offset(3.dp.toPx(), 1.dp.toPx()), Size(layout.size.width + 6.dp.toPx(), layout.size.height + 2.dp.toPx()), CornerRadius(4.dp.toPx()))
    drawText(layout, topLeft = tl)
}

private fun DrawScope.drawRoomLabels(vm: PlanViewModel, tm: TextMeasurer, c: PlanColors) {
    if (vm.zoom < 0.08f) return
    for (room in vm.project.rooms) {
        val center = room.center ?: continue
        val s = vm.toScreen(center)
        val text = room.name + "\n" + String.format(Locale.getDefault(), "%.2f m²", room.areaM2)
        val layout = tm.measure(
            text, TextStyle(fontSize = 13.sp, color = c.label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        )
        val tl = s - Offset(layout.size.width / 2f, layout.size.height / 2f)
        drawRoundRect(
            c.labelBg, tl - Offset(6.dp.toPx(), 3.dp.toPx()),
            Size(layout.size.width + 12.dp.toPx(), layout.size.height + 6.dp.toPx()), CornerRadius(6.dp.toPx()),
        )
        drawText(layout, topLeft = tl)
    }
}

private fun DrawScope.drawTag(tm: TextMeasurer, text: String, center: Offset, bg: Color, fg: Color) {
    val layout = tm.measure(text, TextStyle(fontSize = 13.sp, color = fg, fontWeight = FontWeight.Bold))
    val tl = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    drawRoundRect(
        bg, tl - Offset(8.dp.toPx(), 4.dp.toPx()),
        Size(layout.size.width + 16.dp.toPx(), layout.size.height + 8.dp.toPx()), CornerRadius(8.dp.toPx()),
    )
    drawText(layout, topLeft = tl)
}
