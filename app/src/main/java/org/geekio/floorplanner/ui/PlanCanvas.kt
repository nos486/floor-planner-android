package org.geekio.floorplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import org.geekio.floorplanner.core.FloorPatterns
import org.geekio.floorplanner.core.Furniture
import org.geekio.floorplanner.core.FurnitureCategory
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.Layer
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.PlanDiff
import org.geekio.floorplanner.core.PlanOps
import org.geekio.floorplanner.core.Selection
import org.geekio.floorplanner.core.SnapType
import org.geekio.floorplanner.core.Sun
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.core.Walls
import org.geekio.floorplanner.model.Door
import org.geekio.floorplanner.model.Floor
import org.geekio.floorplanner.model.PlanWindow
import org.geekio.floorplanner.model.Project
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor

private fun P.o() = Offset(x.toFloat(), y.toFloat())

val ELECTRICAL_COLOR = Color(0xFFD97706)
val PLUMBING_COLOR = Color(0xFF0284C7)
val REMOVED_COLOR = Color(0xFFDC2626)
val ISSUE_COLOR = Color(0xFFEA580C)
val ADDED_COLOR = Color(0xFF16A34A)


/** What to draw and where: screen = world * [zoom] + [pan]. */
class PlanView(
    val project: Project,
    val pan: Offset,
    val zoom: Float,
    val settings: Settings,
    val selection: Selection? = null,
    val draft: Draft? = null,
    val group: Set<Selection> = emptySet(),
    /** The floor below, drawn faintly so walls can be lined up. */
    val below: Floor? = null,
    /** Photo of a paper plan under the drawing, placed by [Project.background]. */
    val backgroundImage: ImageBitmap? = null,
    /** When comparing with a saved version: walls taken out are shown in red, new walls in green. */
    val diff: PlanDiff? = null,
) {
    fun isSelected(kind: Kind, id: String) =
        (selection?.kind == kind && selection.id == id) || Selection(kind, id) in group ||
            // Doors and windows light up with their selected wall.
            (kind == Kind.DOOR && project.door(id)?.let { Selection(Kind.WALL, it.wallId) in group } == true) ||
            (kind == Kind.WINDOW && project.window(id)?.let { Selection(Kind.WALL, it.wallId) in group } == true)

    fun toScreen(p: P) = Offset((p.x * zoom).toFloat() + pan.x, (p.y * zoom).toFloat() + pan.y)
    fun toWorld(s: Offset) = P(((s.x - pan.x) / zoom).toDouble(), ((s.y - pan.y) / zoom).toDouble())
}

data class PlanColors(
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
) {
    companion object {
        /** Black on white, for images and PDFs. */
        val print = PlanColors(
            background = Color.White,
            gridMinor = Color(0x22000000),
            gridMajor = Color(0x44000000),
            wall = Color(0xFF2B2F36),
            selected = Color(0xFF2B6CB0),
            window = Color(0xFF2B7BD6),
            label = Color(0xFF1F2937),
            labelBg = Color(0xCCFFFFFF),
            dim = Color(0xFF4B5563),
            itemFill = Color(0xFFF1F3F6),
            itemStroke = Color(0xFF4B5563),
        )
    }
}

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
        val v = vm.view()
        drawPlan(v, colors, textMeasurer)
        val project = v.project
        val sel = v.selection
        if (vm.sunlight) drawSunlight(vm, project, textMeasurer, colors)
        (vm.draft as? Draft.MeasureDraft)?.let { d ->
            Dimensions.geometry(d.a, d.b, 0.0)?.let { drawDimensionLine(v, it, textMeasurer, colors.selected, colors) }
            drawCircle(colors.selected, 5.dp.toPx(), vm.toScreen(d.a))
            if (d.snap == SnapType.ENDPOINT) {
                drawCircle(colors.selected, 12.dp.toPx(), vm.toScreen(d.b), style = Stroke(2.dp.toPx()))
            }
            drawCircle(colors.selected, 5.dp.toPx(), vm.toScreen(d.b))
        }

        (vm.draft as? Draft.BoxDraft)?.let { d ->
            val a = vm.toScreen(d.a)
            val b = vm.toScreen(d.b)
            val tl = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
            val size = Size(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.y - b.y))
            drawRect(colors.selected.copy(alpha = 0.08f), tl, size)
            drawRect(colors.selected, tl, size, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        }
        (vm.draft as? Draft.ArrowDraft)?.let { d ->
            drawArrow(vm.toScreen(d.a), vm.toScreen(d.b), colors.selected, 2.dp.toPx(), 12.dp.toPx())
            drawCircle(colors.selected, 5.dp.toPx(), vm.toScreen(d.a))
        }
        (vm.draft as? Draft.WallDraft)?.let { d ->
            val upm = project.scale.unitsPerMeter
            drawTag(textMeasurer, Units.length(d.a.dist(d.b) / upm, v.settings.units),
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

        // Problems found by the plan check
        for (issue in vm.issues) {
            val s = vm.toScreen(issue.at)
            drawCircle(ISSUE_COLOR.copy(alpha = 0.18f), 22.dp.toPx(), s)
            drawCircle(ISSUE_COLOR, 11.dp.toPx(), s)
            drawLine(Color.White, s - Offset(0f, 5.dp.toPx()), s + Offset(0f, 1.5f.dp.toPx()), 2.5f.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 1.5f.dp.toPx(), s + Offset(0f, 5.dp.toPx()))
        }

        // Endpoint handles of the selected wall
        if (sel?.kind == Kind.WALL) {
            project.wall(sel.id)?.let { wall ->
                for (pt in listOf(wall.a, wall.b)) {
                    val s = vm.toScreen(pt)
                    drawCircle(colors.background, 10.dp.toPx(), s)
                    drawCircle(colors.selected, 10.dp.toPx(), s, style = Stroke(3.dp.toPx()))
                }
                // Bend handle in the middle of the wall
                if ((vm.toScreen(wall.a) - vm.toScreen(wall.b)).getDistance() > 80.dp.toPx()) {
                    val m = vm.toScreen(Walls.apex(wall))
                    val r = 7.dp.toPx()
                    val diamond = Path().apply {
                        moveTo(m.x, m.y - r); lineTo(m.x + r, m.y); lineTo(m.x, m.y + r); lineTo(m.x - r, m.y); close()
                    }
                    drawPath(diamond, colors.background)
                    drawPath(diamond, colors.selected, style = Stroke(2.5f.dp.toPx()))
                }
            }
        }
    }
}


/** Everything that is part of the plan (no editing handles), drawn for [v]. Used on screen and for exports. */
fun DrawScope.drawPlan(v: PlanView, colors: PlanColors, tm: TextMeasurer) {
    drawRect(colors.background)
    if (v.settings.showGrid) drawGrid(v, colors)

    val project = v.project
    val zoom = v.zoom
    val sel = v.selection
    val px = 1f / zoom // one screen pixel in world units

    withTransform({
        translate(v.pan.x, v.pan.y)
        scale(zoom, zoom, pivot = Offset.Zero)
    }) {
        // Background photo
        val bgImage = v.backgroundImage
        val bg = project.background
        if (bgImage != null && bg != null) {
            withTransform({
                translate(bg.x.toFloat(), bg.y.toFloat())
                scale(bg.scale.toFloat(), bg.scale.toFloat(), pivot = Offset.Zero)
            }) {
                drawImage(bgImage, alpha = bg.opacity.toFloat())
            }
        }

        // Walls of the floor below
        v.below?.let { below ->
            for (wall in below.walls) {
                Walls.path(wall).zipWithNext { a, b ->
                    drawLine(colors.wall.copy(alpha = 0.13f), a.o(), b.o(), wall.thickness.toFloat(), cap = StrokeCap.Round)
                }
            }
        }

        // Rooms
        project.rooms.forEachIndexed { i, room ->
            val poly = room.polygon
            if (poly.size < 3) return@forEachIndexed
            val path = Path().apply {
                moveTo(poly[0].x.toFloat(), poly[0].y.toFloat())
                poly.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
                close()
            }
            val isSel = v.isSelected(Kind.ROOM, room.id)
            val custom = room.color != null
            val base = Color(FloorPatterns.parseColor(FloorPatterns.colorFor(room.color, i)) ?: 0xFF4F8CFF)
            drawPath(path, base.copy(alpha = if (custom) (if (isSel) 0.8f else 0.6f) else (if (isSel) 0.35f else 0.15f)))
            if (room.floor != null && FloorPatterns.spacing(room.floor) * zoom >= 4) {
                val min = P(poly.minOf { it.x }, poly.minOf { it.y })
                val max = P(poly.maxOf { it.x }, poly.maxOf { it.y })
                clipPath(path) {
                    for ((a, b) in FloorPatterns.lines(room.floor, min, max)) {
                        drawLine(colors.wall.copy(alpha = 0.22f), a.o(), b.o(), maxOf(0.8f, px))
                    }
                }
            }
        }

        // Furniture, then electrical and plumbing symbols on top
        for (item in project.items.sortedBy { Furniture.isFixture(it) }) {
            val isSel = v.isSelected(Kind.ITEM, item.id)
            val stroke = when (Furniture.type(item.type)?.category) {
                FurnitureCategory.ELECTRICAL -> ELECTRICAL_COLOR
                FurnitureCategory.PLUMBING -> PLUMBING_COLOR
                else -> colors.itemStroke
            }
            drawItem(
                item,
                if (isSel) colors.selected.copy(alpha = 0.18f) else colors.itemFill,
                if (isSel) colors.selected else stroke,
                maxOf(1.2f, 1.5f * px),
            )
        }

        // Walls
        for (wall in project.walls) {
            val isSel = v.isSelected(Kind.WALL, wall.id)
            val base = if (isSel) colors.selected else colors.wall
            // Low walls are drawn lighter.
            val color = if (wall.height != null) base.copy(alpha = base.alpha * 0.45f) else base
            if (wall.isCurved) {
                val pts = Walls.path(wall)
                val path = Path().apply {
                    moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                    pts.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
                }
                drawPath(path, color, style = Stroke(wall.thickness.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawLine(color, wall.a.o(), wall.b.o(), strokeWidth = wall.thickness.toFloat(), cap = StrokeCap.Round)
            }
        }

        // Changes against a saved version
        v.diff?.let { d ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(12 * px, 8 * px))
            for (wall in d.removedWalls) {
                val pts = Walls.path(wall)
                val path = Path().apply {
                    moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                    pts.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
                }
                drawPath(path, REMOVED_COLOR.copy(alpha = 0.25f), style = Stroke(wall.thickness.toFloat(), cap = StrokeCap.Round))
                drawPath(path, REMOVED_COLOR, style = Stroke(maxOf(2f, 2.5f * px), pathEffect = dash))
            }
            for (wall in d.addedWalls) {
                Walls.path(wall).zipWithNext { a, b ->
                    drawLine(ADDED_COLOR, a.o(), b.o(), (wall.thickness * 0.45).toFloat(), cap = StrokeCap.Round)
                }
            }
        }

        // Openings
        for (door in project.doors) {
            drawDoor(v, door, v.isSelected(Kind.DOOR, door.id), colors, px, ghost = false)
        }
        for (win in project.windows) {
            drawWindow(v, win, v.isSelected(Kind.WINDOW, win.id), colors, px, ghost = false)
        }

        // Draft
        when (val d = v.draft) {
            is Draft.WallDraft -> drawLine(
                colors.selected.copy(alpha = 0.6f), d.a.o(), d.b.o(),
                strokeWidth = v.settings.wallThickness.toFloat(), cap = StrokeCap.Round,
            )
            is Draft.OpeningDraft -> {
                if (d.isDoor) drawDoor(v, Door("draft", d.wallId, d.offset, d.width), true, colors, px, ghost = true)
                else drawWindow(v, PlanWindow("draft", d.wallId, d.offset, d.width), true, colors, px, ghost = true)
            }
            // Drawn on top in screen space below.
            is Draft.MeasureDraft, is Draft.ArrowDraft, is Draft.BoxDraft, null -> Unit
        }
    }

    // ----- Screen-space overlays -----
    val dims = Layer.DIMENSIONS !in v.settings.hiddenLayers
    if (dims) drawDimensions(v, tm, colors)
    if (dims && v.settings.showOverall) {
        for (g in Dimensions.overall(project, (34.dp.toPx() / zoom).toDouble())) {
            drawDimensionLine(v, g, tm, colors.dim, colors)
        }
    }
    for (d in project.dimensions) {
        val g = Dimensions.geometry(d) ?: continue
        val isSel = v.isSelected(Kind.DIMENSION, d.id)
        drawDimensionLine(v, g, tm, if (isSel) colors.selected else colors.label, colors)
    }
    drawRoomLabels(v, tm, colors)
    drawLabels(v, tm, colors)
}

private fun DrawScope.drawLabels(v: PlanView, tm: TextMeasurer, c: PlanColors) {
    for (l in v.project.labels) {
        val fontPx = (l.size * v.zoom).toFloat()
        if (fontPx < 4f) continue
        val selected = v.isSelected(Kind.LABEL, l.id)
        val color = if (selected) c.selected else c.label
        val anchor = v.toScreen(l.anchor)
        l.tip?.let { tip -> drawArrow(anchor, v.toScreen(tip), color, maxOf(1.5f, fontPx * 0.08f), fontPx * 0.6f) }
        val layout = tm.measure(l.text, TextStyle(fontSize = fontPx.toSp(), color = color, fontWeight = FontWeight.Medium))
        val tl = anchor - Offset(layout.size.width / 2f, layout.size.height / 2f)
        val pad = fontPx * 0.2f
        drawRoundRect(
            c.labelBg, tl - Offset(pad, pad / 2),
            Size(layout.size.width + 2 * pad, layout.size.height + pad), CornerRadius(pad),
        )
        if (selected) {
            drawRoundRect(
                c.selected, tl - Offset(pad, pad / 2),
                Size(layout.size.width + 2 * pad, layout.size.height + pad), CornerRadius(pad), style = Stroke(1.5f),
            )
        }
        drawText(layout, topLeft = tl)
    }
}

/** A line from [from] to [tip] with a filled head of length [head], all in screen pixels. */
fun DrawScope.drawArrow(from: Offset, tip: Offset, color: Color, width: Float, head: Float) {
    if ((tip - from).getDistance() < 1f) return
    drawLine(color, from, tip, width, cap = StrokeCap.Round)
    val (h1, h2) = PlanOps.arrowHead(P(from.x.toDouble(), from.y.toDouble()), P(tip.x.toDouble(), tip.y.toDouble()), head.toDouble())
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(h1.x.toFloat(), h1.y.toFloat())
        lineTo(h2.x.toFloat(), h2.y.toFloat())
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawDoor(v: PlanView, door: Door, selected: Boolean, c: PlanColors, px: Float, ghost: Boolean) {
    val wall = v.project.wall(door.wallId) ?: return
    val g = PlanOps.openingGeometry(wall, door) ?: return
    val color = if (selected) c.selected else c.wall
    drawLine(if (ghost) c.selected.copy(alpha = 0.4f) else c.background, g.start.o(), g.end.o(), (wall.thickness + 2).toFloat())
    for (s in PlanOps.doorLeaves(g, door)) {
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
    if (door.style == Door.SLIDING) {
        for ((a, b) in PlanOps.slidingPanels(g)) drawLine(color, a.o(), b.o(), strokeWidth = maxOf(3f, 2 * px))
    }
}

private fun DrawScope.drawWindow(v: PlanView, win: PlanWindow, selected: Boolean, c: PlanColors, px: Float, ghost: Boolean) {
    val wall = v.project.wall(win.wallId) ?: return
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

private fun DrawScope.drawGrid(v: PlanView, c: PlanColors) {
    val scale = v.project.scale
    val grid = scale.gridCm * scale.unitsPerMeter / 100
    val meter = scale.unitsPerMeter
    val zoom = v.zoom
    val tl = v.toWorld(Offset.Zero)
    val br = v.toWorld(Offset(size.width, size.height))

    fun lines(step: Double, color: Color, width: Float) {
        if (step * zoom < 6) return
        var x = floor(tl.x / step) * step
        val endX = ceil(br.x / step) * step
        while (x <= endX) {
            val sx = (x * zoom).toFloat() + v.pan.x
            drawLine(color, Offset(sx, 0f), Offset(sx, size.height), width)
            x += step
        }
        var y = floor(tl.y / step) * step
        val endY = ceil(br.y / step) * step
        while (y <= endY) {
            val sy = (y * zoom).toFloat() + v.pan.y
            drawLine(color, Offset(0f, sy), Offset(size.width, sy), width)
            y += step
        }
    }
    lines(grid, c.gridMinor, 1f)
    lines(meter, c.gridMajor, 1.5f)
}

private fun DrawScope.drawDimensions(v: PlanView, tm: TextMeasurer, c: PlanColors) {
    val upm = v.project.scale.unitsPerMeter
    val style = TextStyle(fontSize = 11.sp, color = c.dim)
    for (wall in v.project.walls) {
        val a = v.toScreen(wall.a)
        val b = v.toScreen(wall.b)
        if ((a - b).getDistance() < 70.dp.toPx()) continue
        val dir = (wall.b - wall.a).normalized()
        val mid = Walls.apex(wall)
        val offWorld = mid + dir.perp() * (wall.thickness / 2)
        val screenOff = v.toScreen(offWorld) + Offset(-dir.y.toFloat(), dir.x.toFloat()) * 12.dp.toPx()
        val layout = tm.measure(Units.lengthShort(Walls.length(wall) / upm, v.settings.units), style)
        drawText(layout, topLeft = screenOff - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** A dimension line with extension lines, slash ticks and its length, drawn in screen space. */
private fun DrawScope.drawDimensionLine(v: PlanView, g: DimensionGeometry, tm: TextMeasurer, color: Color, c: PlanColors) {
    val start = v.toScreen(g.start)
    val end = v.toScreen(g.end)
    val len = (end - start).getDistance()
    if (len < 8.dp.toPx()) return
    val dir = (end - start) / len
    val n = Offset(g.normal.x.toFloat(), g.normal.y.toFloat())
    val over = n * (6.dp.toPx() * g.side.toFloat())
    val w = 1.dp.toPx()
    drawLine(color.copy(alpha = 0.6f), v.toScreen(g.fromA), start + over, w)
    drawLine(color.copy(alpha = 0.6f), v.toScreen(g.fromB), end + over, w)
    drawLine(color, start, end, 1.3f * w)
    val tick = (dir + n) * (5.dp.toPx())
    drawLine(color, start - tick, start + tick, 1.6f * w)
    drawLine(color, end - tick, end + tick, 1.6f * w)
    val text = Units.lengthShort(g.start.dist(g.end) / v.project.scale.unitsPerMeter, v.settings.units)
    val layout = tm.measure(text, TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium))
    val side = if (g.side == 0.0) -1f else g.side.toFloat()
    val center = (start + end) / 2f + n * (side * 10.dp.toPx())
    val tl = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    drawRoundRect(c.labelBg, tl - Offset(3.dp.toPx(), 1.dp.toPx()), Size(layout.size.width + 6.dp.toPx(), layout.size.height + 2.dp.toPx()), CornerRadius(4.dp.toPx()))
    drawText(layout, topLeft = tl)
}

private fun DrawScope.drawRoomLabels(v: PlanView, tm: TextMeasurer, c: PlanColors) {
    if (v.zoom < 0.08f) return
    for (room in v.project.rooms) {
        val center = room.center ?: continue
        val s = v.toScreen(center)
        val text = room.name + "\n" + Units.area(room.areaM2, v.settings.units)
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

val SUN_COLOR = Color(0xFFFACC15)

/** Light patches through the windows, clipped to their rooms, and a compass with the sun's direction. */
private fun DrawScope.drawSunlight(vm: PlanViewModel, project: Project, tm: TextMeasurer, colors: PlanColors) {
    for (patch in vm.lightPatches) {
        val room = project.room(patch.roomId) ?: continue
        val roomPath = Path().apply {
            room.polygon.forEachIndexed { i, pt -> vm.toScreen(pt).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        val light = Path().apply {
            patch.polygon.forEachIndexed { i, pt -> vm.toScreen(pt).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        clipPath(roomPath) {
            drawPath(light, SUN_COLOR.copy(alpha = 0.45f))
            drawPath(light, SUN_COLOR, style = Stroke(1.dp.toPx()))
        }
    }

    // Compass in the top-left corner
    val r = 30.dp.toPx()
    val c = Offset(16.dp.toPx() + r, 16.dp.toPx() + r)
    drawCircle(colors.labelBg, r + 6.dp.toPx(), c)
    drawCircle(colors.dim, r, c, style = Stroke(1.dp.toPx()))
    val n = Sun.northVector(project)
    val tip = c + Offset((n.x * r).toFloat(), (n.y * r).toFloat())
    drawArrow(c - Offset((n.x * r * 0.6).toFloat(), (n.y * r * 0.6).toFloat()), tip, colors.label, 2.dp.toPx(), 9.dp.toPx())
    val nText = tm.measure("N", TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.label))
    val nAt = c + Offset((n.x * (r + 12.dp.toPx())).toFloat(), (n.y * (r + 12.dp.toPx())).toFloat())
    drawRoundRect(colors.labelBg, nAt - Offset(8.dp.toPx(), 8.dp.toPx()), Size(16.dp.toPx(), 16.dp.toPx()), CornerRadius(8.dp.toPx()))
    drawText(nText, topLeft = nAt - Offset(nText.size.width / 2f, nText.size.height / 2f))
    val sun = vm.sunPosition
    if (sun.isUp) {
        val s = Sun.towardSun(project, sun)
        // Nearer the middle the higher the sun is.
        val k = r * kotlin.math.cos(Math.toRadians(sun.elevation)).toFloat()
        val at = c + Offset((s.x * k).toFloat(), (s.y * k).toFloat())
        drawCircle(SUN_COLOR, 7.dp.toPx(), at)
        drawCircle(Color(0xFFB45309), 7.dp.toPx(), at, style = Stroke(1.5f.dp.toPx()))
    }
}
