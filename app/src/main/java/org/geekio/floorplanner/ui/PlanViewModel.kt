package org.geekio.floorplanner.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.PlanOps
import org.geekio.floorplanner.core.ProjectIO
import org.geekio.floorplanner.core.Selection
import org.geekio.floorplanner.core.SnapOptions
import org.geekio.floorplanner.core.SnapType
import org.geekio.floorplanner.core.Snapper
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tool { SELECT, WALL, DOOR, WINDOW }

/** Something being drawn that is not part of the project yet. */
sealed interface Draft {
    data class WallDraft(val a: P, val b: P, val snap: SnapType) : Draft
    data class OpeningDraft(val wallId: String, val offset: Double, val width: Double, val isDoor: Boolean) : Draft
}

data class Settings(
    val showGrid: Boolean = true,
    val snap: SnapOptions = SnapOptions(),
    val wallThickness: Double = 20.0,
    val doorWidth: Double = 90.0,
    val windowWidth: Double = 120.0,
)

class PlanViewModel(app: Application) : AndroidViewModel(app) {
    // Null when there is no app storage (screenshot tests).
    private val autosaveFile: File? = runCatching { File(app.filesDir, "autosave.json") }.getOrNull()

    var project by mutableStateOf(Project())
        private set
    var tool by mutableStateOf(Tool.WALL)
        private set
    var selection by mutableStateOf<Selection?>(null)
        private set
    var draft by mutableStateOf<Draft?>(null)
        private set
    var settings by mutableStateOf(Settings())
        private set

    // Viewport: screen = world * zoom + pan
    var pan by mutableStateOf(Offset.Zero)
        private set
    var zoom by mutableStateOf(1f)
        private set
    private var viewSize = IntSize.Zero
    private var fittedOnce = false

    /** Screen density, used to turn finger-sized tolerances into world units. */
    var density = 2.5f

    /** String resource of a one-off message to show in a snackbar. */
    var message by mutableStateOf<Int?>(null)

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var saveJob: Job? = null

    init {
        autosaveFile?.takeIf { it.exists() }
            ?.let { file -> runCatching { file.readText() }.getOrNull() }
            ?.let { ProjectIO.fromJson(it) }
            ?.let { project = it }
        if (project.walls.isNotEmpty()) tool = Tool.SELECT
    }

    // ---------------- History ----------------

    /** Applies [newProject] as one undoable step. */
    private fun commit(newProject: Project, before: Project = project) {
        if (newProject == before) return
        undoStack.addLast(before)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        project = newProject
        changed()
    }

    private fun changed() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        selection?.let { sel ->
            val exists = when (sel.kind) {
                Kind.WALL -> project.wall(sel.id) != null
                Kind.DOOR -> project.door(sel.id) != null
                Kind.WINDOW -> project.window(sel.id) != null
                Kind.ROOM -> project.room(sel.id) != null
            }
            if (!exists) selection = null
        }
        scheduleSave()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(project)
        project = prev
        changed()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(project)
        project = next
        changed()
    }

    private fun scheduleSave() {
        val file = autosaveFile ?: return
        saveJob?.cancel()
        val snapshot = project
        saveJob = viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) {
                runCatching { file.writeText(ProjectIO.toJson(snapshot)) }
            }
        }
    }

    // ---------------- Project ----------------

    fun newProject(name: String) {
        commit(Project(projectName = name.trim().ifEmpty { "Floor Plan" }, scale = project.scale))
        selection = null
        tool = Tool.WALL
        fitToScreen()
    }

    fun renameProject(name: String) {
        commit(project.copy(projectName = name.trim().ifEmpty { project.projectName }))
    }

    fun loadJson(text: String?): Boolean {
        val loaded = text?.let { ProjectIO.fromJson(it) } ?: return false
        commit(loaded)
        selection = null
        tool = Tool.SELECT
        fitToScreen()
        return true
    }

    fun exportJson() = ProjectIO.toJson(project)
    fun exportSvg() = ProjectIO.toSvg(project)

    fun setGridCm(cm: Double) {
        commit(project.copy(scale = project.scale.copy(gridCm = cm.coerceIn(5.0, 200.0))))
    }

    fun updateSettings(s: Settings) {
        settings = s
    }

    /** Shows [p] without history or autosave; used to render store screenshots. */
    fun showPreview(p: Project, tool: Tool, selection: Selection? = null) {
        project = PlanOps.withRooms(p)
        this.tool = tool
        this.selection = selection
    }

    // ---------------- Tools & selection ----------------

    fun selectTool(t: Tool) {
        tool = t
        draft = null
        if (t != Tool.SELECT) selection = null
    }

    fun clearSelection() {
        selection = null
    }

    fun deleteSelected() {
        val sel = selection ?: return
        commit(PlanOps.delete(project, sel))
        selection = null
    }

    fun setWallThickness(id: String, t: Double) = commit(PlanOps.setWallThickness(project, id, t))

    fun setWallLengthMeters(id: String, meters: Double) =
        commit(PlanOps.setWallLength(project, id, meters * project.scale.unitsPerMeter))

    fun splitWall(id: String) {
        val (p, first) = PlanOps.splitWall(project, id) ?: run { message = R.string.msg_wall_too_short; return }
        commit(p)
        selection = Selection(Kind.WALL, first)
    }

    fun setOpeningWidth(kind: Kind, id: String, width: Double) = commit(PlanOps.setOpeningWidth(project, kind, id, width))

    fun flipDoorSide(id: String) = commit(PlanOps.updateDoor(project, id) { it.copy(side = if (it.side < 0) 1 else -1) })

    fun flipDoorHinge(id: String) = commit(PlanOps.updateDoor(project, id) { it.copy(flipped = !it.flipped) })

    fun renameRoom(id: String, name: String) = commit(PlanOps.renameRoom(project, id, name))

    // ---------------- Viewport ----------------

    fun toWorld(s: Offset) = P(((s.x - pan.x) / zoom).toDouble(), ((s.y - pan.y) / zoom).toDouble())
    fun toScreen(p: P) = Offset((p.x * zoom).toFloat() + pan.x, (p.y * zoom).toFloat() + pan.y)

    /** [px] screen pixels expressed in world units. */
    private fun worldTol(dp: Float) = (dp * density / zoom).toDouble()

    fun onViewSize(size: IntSize) {
        val first = viewSize == IntSize.Zero
        viewSize = size
        if (first && !fittedOnce) fitToScreen()
    }

    fun fitToScreen() {
        if (viewSize.width == 0 || viewSize.height == 0) return
        fittedOnce = true
        val pts = project.walls.flatMap { listOf(it.a, it.b) }
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat()
        if (pts.isEmpty()) {
            zoom = w / 600f // about 6 m across
            pan = Offset(w / 2, h / 2)
            return
        }
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val bw = maxOf(maxX - minX, 100.0) + 120
        val bh = maxOf(maxY - minY, 100.0) + 120
        zoom = minOf(w / bw, h / bh).toFloat().coerceIn(MIN_ZOOM, MAX_ZOOM)
        val cx = ((minX + maxX) / 2).toFloat()
        val cy = ((minY + maxY) / 2).toFloat()
        pan = Offset(w / 2 - cx * zoom, h / 2 - cy * zoom)
    }

    fun onTransform(centroid: Offset, panDelta: Offset, zoomChange: Float) {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val p = pan + panDelta
        pan = centroid - (centroid - p) * (newZoom / zoom)
        zoom = newZoom
    }

    // ---------------- Gestures ----------------

    private sealed interface DragOp {
        data class Pan(var last: Offset) : DragOp
        data class DrawWall(val start: P) : DragOp
        data class PlaceOpening(val isDoor: Boolean) : DragOp
        data class MoveVertex(val origin: Project, val from: P) : DragOp
        data class MoveWall(val origin: Project, val wallId: String, val startWorld: P) : DragOp
        data class SlideOpening(val origin: Project, val kind: Kind, val id: String, val grab: Double) : DragOp
    }

    private var dragOp: DragOp? = null

    private val gridSize get() = project.scale.gridCm * project.scale.unitsPerMeter / 100

    private fun snap(point: P, start: P? = null, exclude: P? = null, walls: List<Wall> = project.walls) =
        Snapper.resolve(point, walls, gridSize, worldTol(SNAP_RADIUS_DP), settings.snap, start, exclude)

    fun onTap(screen: Offset) {
        val w = toWorld(screen)
        when (tool) {
            Tool.SELECT -> selection = PlanOps.hit(project, w, worldTol(HIT_DP))
            Tool.WALL -> message = R.string.hint_wall
            Tool.DOOR, Tool.WINDOW -> {
                updateOpeningDraft(w, tool == Tool.DOOR)
                placeOpeningDraft()
            }
        }
    }

    fun onDragStart(screen: Offset) {
        val w = toWorld(screen)
        dragOp = when (tool) {
            Tool.WALL -> {
                val s = snap(w)
                draft = Draft.WallDraft(s.point, s.point, s.type)
                DragOp.DrawWall(s.point)
            }
            Tool.DOOR, Tool.WINDOW -> {
                updateOpeningDraft(w, tool == Tool.DOOR)
                DragOp.PlaceOpening(tool == Tool.DOOR)
            }
            Tool.SELECT -> selectDragStart(screen, w)
        }
    }

    private fun selectDragStart(screen: Offset, w: P): DragOp {
        val tol = worldTol(HIT_DP)
        // Endpoint handles of the selected wall.
        val sel = selection
        if (sel?.kind == Kind.WALL) {
            project.wall(sel.id)?.let { wall ->
                val handleTol = worldTol(HANDLE_DP)
                listOf(wall.a, wall.b).minByOrNull { it.dist(w) }?.takeIf { it.dist(w) <= handleTol }?.let {
                    return DragOp.MoveVertex(project, it)
                }
            }
        }
        PlanOps.hitOpening(project, w, tol)?.let { hit ->
            selection = hit
            val o = if (hit.kind == Kind.DOOR) project.door(hit.id) else project.window(hit.id)
            val wall = o?.let { project.wall(it.wallId) }
            if (o != null && wall != null) {
                val along = (w - wall.a).dot((wall.b - wall.a).normalized())
                return DragOp.SlideOpening(project, hit.kind, hit.id, o.offset - along)
            }
        }
        PlanOps.hitWall(project, w, tol)?.let { wall ->
            selection = Selection(Kind.WALL, wall.id)
            return DragOp.MoveWall(project, wall.id, w)
        }
        return DragOp.Pan(screen)
    }

    fun onDrag(screen: Offset) {
        val w = toWorld(screen)
        when (val op = dragOp) {
            is DragOp.Pan -> {
                pan += screen - op.last
                op.last = screen
            }
            is DragOp.DrawWall -> {
                val s = snap(w, start = op.start, exclude = op.start)
                draft = Draft.WallDraft(op.start, s.point, s.type)
            }
            is DragOp.PlaceOpening -> updateOpeningDraft(w, op.isDoor)
            is DragOp.MoveVertex -> {
                val others = op.origin.walls
                val s = snap(w, exclude = op.from, walls = others)
                project = PlanOps.moveVertex(op.origin, op.from, s.point)
            }
            is DragOp.MoveWall -> {
                val delta = w - op.startWorld
                val snapped = if (settings.snap.grid) Snapper.snapToGrid(delta, gridSize) else delta
                project = PlanOps.translateWall(op.origin, op.wallId, snapped)
            }
            is DragOp.SlideOpening -> {
                val o = if (op.kind == Kind.DOOR) op.origin.door(op.id) else op.origin.window(op.id)
                val wall = o?.let { op.origin.wall(it.wallId) } ?: return
                val along = (w - wall.a).dot((wall.b - wall.a).normalized()) + op.grab
                val half = o.width / 2
                val offset = along.coerceIn(half, maxOf(half, wall.length - half))
                val rounded = if (settings.snap.grid) Math.round(offset / 5) * 5.0 else offset
                project = if (op.kind == Kind.DOOR) PlanOps.updateDoor(op.origin, op.id) { it.copy(offset = rounded) }
                else PlanOps.updateWindow(op.origin, op.id) { it.copy(offset = rounded) }
            }
            null -> Unit
        }
    }

    fun onDragEnd() {
        when (val op = dragOp) {
            is DragOp.DrawWall -> {
                val d = draft as? Draft.WallDraft
                if (d != null) {
                    PlanOps.addWall(project, d.a, d.b, settings.wallThickness)?.let { (p, _) -> commit(p) }
                }
                draft = null
            }
            is DragOp.PlaceOpening -> placeOpeningDraft()
            is DragOp.MoveVertex -> commit(project, before = op.origin)
            is DragOp.MoveWall -> commit(project, before = op.origin)
            is DragOp.SlideOpening -> commit(project, before = op.origin)
            is DragOp.Pan, null -> Unit
        }
        dragOp = null
    }

    fun onDragCancel() {
        when (val op = dragOp) {
            is DragOp.MoveVertex -> project = op.origin
            is DragOp.MoveWall -> project = op.origin
            is DragOp.SlideOpening -> project = op.origin
            else -> Unit
        }
        draft = null
        dragOp = null
    }

    private fun updateOpeningDraft(w: P, isDoor: Boolean) {
        val wall = PlanOps.hitWall(project, w, worldTol(OPENING_HIT_DP))
        val width = if (isDoor) settings.doorWidth else settings.windowWidth
        val offset = wall?.let { PlanOps.openingOffset(it, w, width) }
        draft = if (wall != null && offset != null) Draft.OpeningDraft(wall.id, offset, width, isDoor) else null
    }

    private fun placeOpeningDraft() {
        val d = draft as? Draft.OpeningDraft
        draft = null
        if (d == null) {
            message = R.string.hint_opening
            return
        }
        val (p, id) = if (d.isDoor) PlanOps.addDoor(project, d.wallId, d.offset, d.width).let { it.first to it.second.id }
        else PlanOps.addWindow(project, d.wallId, d.offset, d.width).let { it.first to it.second.id }
        commit(p)
        selection = Selection(if (d.isDoor) Kind.DOOR else Kind.WINDOW, id)
    }

    companion object {
        const val MIN_ZOOM = 0.05f
        const val MAX_ZOOM = 20f
        private const val HIT_DP = 14f
        private const val HANDLE_DP = 24f
        private const val SNAP_RADIUS_DP = 18f
        private const val OPENING_HIT_DP = 30f
    }
}
