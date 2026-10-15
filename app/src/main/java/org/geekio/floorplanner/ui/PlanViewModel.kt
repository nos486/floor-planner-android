package org.geekio.floorplanner.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Backgrounds
import org.geekio.floorplanner.core.Compare
import org.geekio.floorplanner.core.Dimensions
import org.geekio.floorplanner.core.Issue
import org.geekio.floorplanner.core.LightPatch
import org.geekio.floorplanner.core.RoomSun
import org.geekio.floorplanner.core.Sun
import org.geekio.floorplanner.core.Samples
import org.geekio.floorplanner.core.SampleText
import org.geekio.floorplanner.core.SunPosition
import org.geekio.floorplanner.core.PlanCheck
import org.geekio.floorplanner.core.PlanDiff
import org.geekio.floorplanner.core.Floors
import org.geekio.floorplanner.core.Furniture
import org.geekio.floorplanner.core.GroupOps
import org.geekio.floorplanner.core.FurnitureType
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.Layer
import org.geekio.floorplanner.core.Layers
import org.geekio.floorplanner.core.P
import org.geekio.floorplanner.core.PlanOps
import org.geekio.floorplanner.core.ProjectEntry
import org.geekio.floorplanner.core.ProjectIO
import org.geekio.floorplanner.core.ProjectStore
import org.geekio.floorplanner.core.Selection
import org.geekio.floorplanner.core.SnapOptions
import org.geekio.floorplanner.core.SnapType
import org.geekio.floorplanner.core.generateId
import org.geekio.floorplanner.core.roundTo
import org.geekio.floorplanner.core.Snapper
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Walls
import org.geekio.floorplanner.model.Costs
import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tool {
    SELECT, WALL, DOOR, WINDOW, MEASURE, TEXT,
    /** Draw a line over the background photo to give it its real scale. */
    CALIBRATE,
    /** Drag the background photo around. */
    BACKGROUND,
}

/** Something being drawn that is not part of the project yet. */
sealed interface Draft {
    data class WallDraft(val a: P, val b: P, val snap: SnapType) : Draft
    data class OpeningDraft(val wallId: String, val offset: Double, val width: Double, val isDoor: Boolean) : Draft
    data class MeasureDraft(val a: P, val b: P, val snap: SnapType) : Draft
    data class ArrowDraft(val a: P, val b: P) : Draft
    data class BoxDraft(val a: P, val b: P) : Draft
}

/** A label waiting for its text: shown as a dialog, then added at [at] with an arrow to [tip] if set. */
data class LabelRequest(val at: P, val tip: P?)

data class Settings(
    val showGrid: Boolean = true,
    val showOverall: Boolean = true,
    val snap: SnapOptions = SnapOptions(),
    val wallThickness: Double = 20.0,
    val doorWidth: Double = 90.0,
    val windowWidth: Double = 120.0,
    /** Text height of new labels, in cm. */
    val labelSize: Double = 25.0,
    val units: UnitSystem = UnitSystem.METRIC,
    val hiddenLayers: Set<Layer> = emptySet(),
)

class PlanViewModel(app: Application) : AndroidViewModel(app) {
    // Null when there is no app storage (screenshot tests).
    private val filesDir: File? = runCatching { app.filesDir }.getOrNull()
    private val store: ProjectStore? = filesDir?.let { ProjectStore(File(it, "projects")) }

    /** Id of the open project, null while the start screen is shown. */
    var currentId by mutableStateOf<String?>(null)
        private set
    /** Saved projects for the start screen, newest first. */
    var projects by mutableStateOf<List<ProjectEntry>>(emptyList())
        private set

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

    // Null when there is no app storage (screenshot tests).
    private val prefs: SharedPreferences? = runCatching { app.getSharedPreferences("settings", Context.MODE_PRIVATE) }.getOrNull()

    init {
        filesDir?.let { store?.migrateLegacy(File(it, "autosave.json")) }
        prefs?.let { settings = loadSettings(it) }
        refreshProjects()
    }

    private fun loadSettings(p: SharedPreferences): Settings {
        val d = Settings()
        fun dbl(key: String, def: Double) = p.getFloat(key, def.toFloat()).toDouble()
        return Settings(
            showGrid = p.getBoolean("showGrid", d.showGrid),
            showOverall = p.getBoolean("showOverall", d.showOverall),
            snap = SnapOptions(
                grid = p.getBoolean("snapGrid", d.snap.grid),
                endpoints = p.getBoolean("snapEndpoints", d.snap.endpoints),
                angles = p.getBoolean("snapAngles", d.snap.angles),
            ),
            wallThickness = dbl("wallThickness", d.wallThickness),
            doorWidth = dbl("doorWidth", d.doorWidth),
            windowWidth = dbl("windowWidth", d.windowWidth),
            labelSize = dbl("labelSize", d.labelSize),
            units = runCatching { UnitSystem.valueOf(p.getString("units", null) ?: "") }.getOrDefault(d.units),
            hiddenLayers = p.getStringSet("hiddenLayers", emptySet()).orEmpty()
                .mapNotNull { name -> Layer.entries.firstOrNull { it.name == name } }.toSet(),
        )
    }

    private fun saveSettings(s: Settings) {
        prefs?.edit()
            ?.putBoolean("showGrid", s.showGrid)
            ?.putBoolean("showOverall", s.showOverall)
            ?.putBoolean("snapGrid", s.snap.grid)
            ?.putBoolean("snapEndpoints", s.snap.endpoints)
            ?.putBoolean("snapAngles", s.snap.angles)
            ?.putFloat("wallThickness", s.wallThickness.toFloat())
            ?.putFloat("doorWidth", s.doorWidth.toFloat())
            ?.putFloat("windowWidth", s.windowWidth.toFloat())
            ?.putFloat("labelSize", s.labelSize.toFloat())
            ?.putString("units", s.units.name)
            ?.putStringSet("hiddenLayers", s.hiddenLayers.map { it.name }.toSet())
            ?.apply()
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
        if (group.isNotEmpty()) group = GroupOps.existing(project, group)
        selection?.let { sel ->
            val exists = when (sel.kind) {
                Kind.WALL -> project.wall(sel.id) != null
                Kind.DOOR -> project.door(sel.id) != null
                Kind.WINDOW -> project.window(sel.id) != null
                Kind.ROOM -> project.room(sel.id) != null
                Kind.ITEM -> project.item(sel.id) != null
                Kind.DIMENSION -> project.dimension(sel.id) != null
                Kind.LABEL -> project.label(sel.id) != null
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
        val store = store ?: return
        val id = currentId ?: return
        saveJob?.cancel()
        val snapshot = project
        saveJob = viewModelScope.launch {
            delay(400)
            withContext(Dispatchers.IO) { store.save(id, snapshot) }
        }
    }

    /** Writes a pending autosave right away (when the app goes to the background or the project is closed). */
    fun flushSave() {
        val job = saveJob ?: return
        if (!job.isActive) return
        job.cancel()
        currentId?.let { store?.save(it, project) }
    }

    // ---------------- Projects ----------------

    fun refreshProjects() {
        val store = store ?: return
        viewModelScope.launch {
            projects = withContext(Dispatchers.IO) { store.list() }
        }
    }

    fun openProject(id: String) {
        val loaded = store?.load(id) ?: run { message = R.string.msg_open_failed; refreshProjects(); return }
        show(id, loaded)
    }

    /** Saves [p] as a new project and opens it. */
    fun createProject(p: Project) {
        val id = store?.create(p) ?: generateId("p")
        show(id, PlanOps.withRooms(p))
    }

    private fun show(id: String, p: Project) {
        saveJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        canUndo = false
        canRedo = false
        currentId = id
        project = p
        selection = null
        draft = null
        tool = if (p.walls.isEmpty()) Tool.WALL else Tool.SELECT
        fittedOnce = false
        fitToScreen()
        loadBackground(id)
        comparing = null
        versions = emptyList()
    }

    // ---------------- Plan check ----------------

    /** True while the list of problems found in the plan is open. */
    var checking by mutableStateOf(false)
        private set

    private var issueCache: Pair<Project, List<Issue>>? = null

    /** Problems in the open floor while [checking], kept up to date as the plan changes. */
    val issues: List<Issue>
        get() {
            if (!checking) return emptyList()
            issueCache?.let { (p, list) -> if (p === project) return list }
            return PlanCheck.run(project).also { issueCache = project to it }
        }

    fun checkPlan() {
        selection = null
        multiSelect = false
        group = emptySet()
        sunlight = false
        checking = true
        if (issues.isEmpty()) {
            checking = false
            message = R.string.msg_no_issues
        }
    }

    fun stopChecking() {
        checking = false
    }

    /** Centres the view on [issue]. */
    fun focusIssue(issue: Issue) {
        pan = Offset(viewSize.width / 2f - (issue.at.x * zoom).toFloat(), viewSize.height / 2f - (issue.at.y * zoom).toFloat())
    }

    // ---------------- Sunlight ----------------

    /** True while the sunlight view is open. */
    var sunlight by mutableStateOf(false)
        private set
    var sunMonth by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)
        private set
    /** Local solar time, 12 = noon. */
    var sunHour by mutableStateOf(12.0)
        private set

    val sunPosition: SunPosition get() = Sun.position(project.latitude, Sun.midMonthDay(sunMonth), sunHour)

    private var patchCache: Triple<Project, SunPosition, List<LightPatch>>? = null
    private var hoursCache: Triple<Project, Int, List<RoomSun>>? = null

    /** Patches of sunlight on the floor of the open floor, empty unless [sunlight]. */
    val lightPatches: List<LightPatch>
        get() {
            if (!sunlight) return emptyList()
            val sun = sunPosition
            patchCache?.let { (p, s, list) -> if (p === project && s == sun) return list }
            return Sun.patches(project, sun).also { patchCache = Triple(project, sun, it) }
        }

    /** Hours of direct sun per room in the middle of [sunMonth]. */
    val roomSun: List<RoomSun>
        get() {
            hoursCache?.let { (p, m, list) -> if (p === project && m == sunMonth) return list }
            return Sun.hours(project, Sun.midMonthDay(sunMonth)).also { hoursCache = Triple(project, sunMonth, it) }
        }

    fun showSunlight() {
        selection = null
        multiSelect = false
        group = emptySet()
        checking = false
        sunlight = true
    }

    fun stopSunlight() {
        sunlight = false
    }

    fun setSunTime(month: Int, hour: Double) {
        sunMonth = month.coerceIn(1, 12)
        sunHour = hour.coerceIn(0.0, 24.0)
    }

    /** Turns the north arrow by [degrees] (clockwise). */
    fun rotateNorth(degrees: Double) = commit(project.copy(north = (project.north + degrees).mod(360.0)))

    fun setLatitude(latitude: Double) = commit(project.copy(latitude = latitude.coerceIn(-66.0, 66.0)))

    // ---------------- Versions ----------------

    /** Saved versions of the open project, newest first. */
    var versions by mutableStateOf<List<ProjectStore.VersionEntry>>(emptyList())
        private set

    /** The version being compared with the plan: its name and its copy of the open floor. */
    var comparing by mutableStateOf<Pair<String, Project>?>(null)
        private set

    private var diffCache: Triple<Project, Project, PlanDiff>? = null

    /** Differences between [comparing] and the plan, or null when not comparing. */
    val diff: PlanDiff?
        get() {
            val other = comparing?.second ?: return null
            diffCache?.let { (a, b, d) -> if (a === other && b === project) return d }
            return Compare.diff(other, project).also { diffCache = Triple(other, project, it) }
        }

    fun refreshVersions() {
        val store = store ?: return
        val id = currentId ?: return
        viewModelScope.launch { versions = withContext(Dispatchers.IO) { store.versions(id) } }
    }

    fun saveVersion(name: String) {
        val store = store ?: return
        val id = currentId ?: return
        val snapshot = project
        val n = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.saveVersion(id, n, snapshot) }
            message = if (ok) R.string.msg_version_saved else R.string.msg_save_failed
            refreshVersions()
        }
    }

    /** Replaces the plan with a saved version (one undo step brings the current plan back). */
    fun restoreVersion(key: String) {
        val id = currentId ?: return
        val p = store?.loadVersion(id, key) ?: return
        comparing = null
        selection = null
        group = emptySet()
        commit(p.copy(projectName = project.projectName))
        fitToScreen()
    }

    fun deleteVersion(key: String) {
        val id = currentId ?: return
        store?.deleteVersion(id, key)
        refreshVersions()
    }

    fun compareWith(entry: ProjectStore.VersionEntry) {
        val id = currentId ?: return
        val p = store?.loadVersion(id, entry.key) ?: return
        comparing = entry.name to Floors.switchTo(p, project.floorIndex)
    }

    fun stopComparing() {
        comparing = null
    }

    // ---------------- Background photo ----------------

    /** The photo under the plan, loaded from the project's image file. */
    var backgroundImage by mutableStateOf<ImageBitmap?>(null)
        private set

    /** A line drawn with the calibrate tool, waiting for its real length. */
    var calibrateRequest by mutableStateOf<Pair<P, P>?>(null)

    private fun loadBackground(id: String) {
        backgroundImage = null
        val file = store?.backgroundFile(id) ?: return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null }
            if (currentId == id) backgroundImage = bmp?.asImageBitmap()
        }
    }

    /** Puts the picture at [uri] under the plan, about as wide as the screen; then the user sets its scale. */
    fun setBackground(uri: Uri) {
        val id = currentId ?: return
        val file = store?.backgroundFile(id) ?: return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val b = ImageFiles.decode(resolver, uri, BACKGROUND_MAX_PX)
                    file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    b
                }.getOrNull()
            }
            if (bmp == null) {
                message = R.string.msg_image_failed
                return@launch
            }
            backgroundImage = bmp.asImageBitmap()
            val center = toWorld(Offset(viewSize.width / 2f, viewSize.height / 2f))
            val span = viewSize.width / zoom * 0.9
            commit(project.copy(background = Backgrounds.place(bmp.width, bmp.height, center, span)))
            selectTool(Tool.CALIBRATE)
        }
    }

    fun removeBackground() {
        currentId?.let { id -> store?.backgroundFile(id)?.delete() }
        backgroundImage = null
        commit(project.copy(background = null))
        if (tool == Tool.CALIBRATE || tool == Tool.BACKGROUND) selectTool(Tool.SELECT)
    }

    fun setBackgroundOpacity(opacity: Double) {
        val bg = project.background ?: return
        commit(project.copy(background = bg.copy(opacity = opacity.coerceIn(0.1, 1.0))))
    }

    /** Gives the background its scale from the line in [calibrateRequest] being [meters] long. */
    fun calibrate(meters: Double) {
        val (a, b) = calibrateRequest ?: return
        calibrateRequest = null
        val bg = project.background ?: return
        commit(project.copy(background = Backgrounds.calibrate(bg, a, b, meters * project.scale.unitsPerMeter)))
        selectTool(Tool.SELECT)
        fitToScreen()
    }

    /** Back to the start screen. */
    fun closeProject() {
        flushSave()
        currentId = null
        selection = null
        refreshProjects()
    }

    fun deleteProject(id: String) {
        store?.delete(id)
        refreshProjects()
    }

    fun duplicateProject(id: String, name: String) {
        store?.duplicate(id, name)
        refreshProjects()
    }

    fun renameProjectEntry(id: String, name: String) {
        store?.rename(id, name)
        refreshProjects()
    }

    /** Imports a JSON project file as a new project; false when it isn't a floor plan. */
    /** Creates a copy of the furnished two-storey sample home and opens it. */
    fun openSample() {
        val app = getApplication<Application>()
        val p = Samples.home({ app.getString(roomKeyName(it)) }) { t ->
            app.getString(
                when (t) {
                    SampleText.NAME -> R.string.sample_name
                    SampleText.GROUND_FLOOR -> R.string.ground_floor
                    SampleText.FIRST_FLOOR -> R.string.first_floor
                    SampleText.ENTRANCE -> R.string.entrance
                },
            )
        }
        createProject(p)
    }

    fun importJson(text: String?): Boolean {
        val loaded = text?.let { ProjectIO.fromJson(it) } ?: return false
        createProject(loaded)
        return true
    }

    // ---------------- Project ----------------

    fun renameProject(name: String) {
        commit(project.copy(projectName = name.trim().ifEmpty { project.projectName }))
    }

    fun exportJson() = ProjectIO.toJson(project)

    fun setGridCm(cm: Double) {
        commit(project.copy(scale = project.scale.copy(gridCm = cm.coerceIn(5.0, 200.0))))
    }

    fun setPrice(price: Double?) = commit(project.copy(price = price))

    fun setEstimateInputs(height: Double, costs: Costs) = commit(project.copy(wallHeight = height, costs = costs))

    fun updateSettings(s: Settings) {
        if (s.hiddenLayers != settings.hiddenLayers) {
            selection = null
            group = emptySet()
        }
        settings = s
        saveSettings(s)
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
        if (t != Tool.SELECT) {
            selection = null
            multiSelect = false
            group = emptySet()
        }
    }

    // ---------------- Multiple selection ----------------

    /** When on, taps add to / remove from [group] and dragging on empty space draws a selection box. */
    var multiSelect by mutableStateOf(false)
        private set
    var group by mutableStateOf<Set<Selection>>(emptySet())
        private set

    fun enableMultiSelect(on: Boolean) {
        multiSelect = on
        group = if (on) setOfNotNull(selection?.let { GroupOps.member(project, it) }) else emptySet()
        selection = null
        if (on) tool = Tool.SELECT
    }

    fun selectAll() {
        group = GroupOps.inBox(shown, P(-1e9, -1e9), P(1e9, 1e9))
    }

    fun duplicateGroup() {
        val (p, copies) = GroupOps.duplicate(project, group, P(50.0, 50.0))
        commit(p)
        group = copies
    }

    fun rotateGroup() = commit(GroupOps.rotate90(project, group))

    fun mirrorGroup() = commit(GroupOps.mirror(project, group))

    fun deleteGroup() {
        commit(GroupOps.delete(project, group))
        group = emptySet()
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

    fun setDoorStyle(id: String, style: String?) = commit(PlanOps.updateDoor(project, id) { it.copy(style = style) })

    fun setWallBulge(id: String, bulge: Double) = commit(PlanOps.setWallBulge(project, id, bulge))

    fun setWallHeight(id: String, height: Double?) = commit(PlanOps.setWallHeight(project, id, height))

    fun flipDoorHinge(id: String) = commit(PlanOps.updateDoor(project, id) { it.copy(flipped = !it.flipped) })

    fun renameRoom(id: String, name: String) = commit(PlanOps.renameRoom(project, id, name))

    fun setRoomColor(id: String, color: String?) = commit(PlanOps.setRoomColor(project, id, color))

    fun setRoomFloor(id: String, floor: String?) = commit(PlanOps.setRoomFloor(project, id, floor))

    // ---------------- Floors ----------------

    private fun onFloor(p: Project) {
        selection = null
        group = emptySet()
        draft = null
        commit(p)
    }

    fun switchFloor(index: Int) = onFloor(Floors.switchTo(project, index))

    /** Adds a floor on top and opens it; [firstName] names the existing floor when the plan had only one. */
    fun addFloor(name: String, firstName: String) = onFloor(Floors.add(project, name, firstName))

    fun deleteFloor(index: Int) = onFloor(Floors.delete(project, index))

    fun renameFloor(index: Int, name: String) = commit(Floors.rename(project, index, name))

    // ---------------- Labels ----------------

    var labelRequest by mutableStateOf<LabelRequest?>(null)

    fun addLabel(text: String) {
        val req = labelRequest ?: return
        labelRequest = null
        val (p, label) = PlanOps.addLabel(project, text, req.at, settings.labelSize, req.tip) ?: return
        commit(p)
        selection = Selection(Kind.LABEL, label.id)
    }

    fun setLabelText(id: String, text: String) {
        if (text.isBlank()) return
        commit(PlanOps.updateLabel(project, id) { it.copy(text = text.trim()) })
    }

    fun setLabelSize(id: String, size: Double) {
        commit(PlanOps.updateLabel(project, id) { it.copy(size = size) })
        updateSettings(settings.copy(labelSize = size))
    }

    fun removeLabelArrow(id: String) = commit(PlanOps.updateLabel(project, id) { it.copy(arrow = null) })

    // ---------------- Furniture ----------------

    /** Adds a [type] item in the middle of the screen (against a wall if one is close) and selects it. */
    fun addFurniture(type: FurnitureType) {
        val center = toWorld(Offset(viewSize.width / 2f, viewSize.height / 2f)).let {
            if (settings.snap.grid) Snapper.snapToGrid(it, gridSize) else it
        }
        var item = Furniture.create(type, center)
        if (settings.snap.endpoints) Furniture.snapToWall(item, center, project.walls, worldTol(WALL_SNAP_DP))?.let { item = it }
        commit(PlanOps.addItem(project, item))
        tool = Tool.SELECT
        draft = null
        selection = Selection(Kind.ITEM, item.id)
    }

    fun rotateItem(id: String, degrees: Double) = commit(PlanOps.rotateItem(project, id, degrees))

    fun setItemRotation(id: String, degrees: Double) =
        commit(PlanOps.updateItem(project, id) { it.copy(rotation = Furniture.normalizeAngle(degrees)) })

    fun setItemSize(id: String, width: Double, depth: Double) = commit(PlanOps.setItemSize(project, id, width, depth))

    fun duplicateItem(id: String) {
        val (p, copy) = PlanOps.duplicateItem(project, id) ?: return
        commit(p)
        selection = Selection(Kind.ITEM, copy.id)
    }

    /** Screen position of the rotation handle of a selected item. */
    fun itemRotateHandle(id: String): Offset? {
        val item = project.item(id) ?: return null
        val front = toScreen(Furniture.toWorld(item, P(0.0, item.depth / 2)))
        val center = toScreen(P(item.x, item.y))
        val dir = front - center
        val len = dir.getDistance()
        if (len < 0.001f) return null
        return front + dir / len * (ROTATE_HANDLE_OFFSET_DP * density)
    }

    // ---------------- Viewport ----------------

    /** The project without its hidden layers: what is drawn, exported and can be tapped. */
    val shown: Project get() = Layers.visible(project, settings.hiddenLayers)

    fun view() = PlanView(shown, pan, zoom, settings, selection, draft, group, Floors.below(project), backgroundImage, diff)

    fun toWorld(s: Offset) = P(((s.x - pan.x) / zoom).toDouble(), ((s.y - pan.y) / zoom).toDouble())
    fun toScreen(p: P) = Offset((p.x * zoom).toFloat() + pan.x, (p.y * zoom).toFloat() + pan.y)

    /** [px] screen pixels expressed in world units. */
    private fun worldTol(dp: Float) = (dp * density / zoom).toDouble()

    fun onViewSize(size: IntSize) {
        viewSize = size
        if (!fittedOnce) fitToScreen()
    }

    fun fitToScreen() {
        if (viewSize.width == 0 || viewSize.height == 0) return
        fittedOnce = true
        val pts = ProjectIO.extentPoints(project)
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

    /** Zoom at which the plan shows at its real size on this screen. */
    val trueScaleZoom: Float
        get() {
            val xdpi = getApplication<Application>().resources.displayMetrics.xdpi
            return (xdpi / 2.54 * 100 / project.scale.unitsPerMeter).toFloat()
        }

    val isTrueScale: Boolean get() = kotlin.math.abs(zoom / trueScaleZoom - 1) < 0.01f

    /** Zooms around the middle of the view so 1 cm on the plan is 1 cm on the glass. */
    fun showTrueScale() {
        val target = trueScaleZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val mid = Offset(viewSize.width / 2f, viewSize.height / 2f)
        pan = mid - (mid - pan) * (target / zoom)
        zoom = target
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
        data class BendWall(val origin: Project, val wallId: String) : DragOp
        data class MoveWall(val origin: Project, val wallId: String, val startWorld: P) : DragOp
        data class SlideOpening(val origin: Project, val kind: Kind, val id: String, val grab: Double) : DragOp
        data class DrawMeasure(val start: P) : DragOp
        data class DrawArrow(val start: P) : DragOp
        data class DrawCalibration(val start: P) : DragOp
        data class MoveBackground(val origin: Project, val startWorld: P) : DragOp
        data class BoxSelect(val start: P) : DragOp
        data class MoveGroup(val origin: Project, val startWorld: P) : DragOp
        data class MoveLabel(val origin: Project, val id: String, val startWorld: P) : DragOp
        data class MoveDimension(val origin: Project, val id: String) : DragOp
        data class MoveItem(val origin: Project, val id: String, val grab: P) : DragOp
        data class RotateItem(val origin: Project, val id: String) : DragOp
    }

    private var dragOp: DragOp? = null

    private val gridSize get() = project.scale.gridCm * project.scale.unitsPerMeter / 100

    private fun snap(point: P, start: P? = null, exclude: P? = null, walls: List<Wall> = project.walls) =
        Snapper.resolve(point, walls, gridSize, worldTol(SNAP_RADIUS_DP), settings.snap, start, exclude)

    fun onTap(screen: Offset) {
        val w = toWorld(screen)
        when (tool) {
            Tool.SELECT -> if (multiSelect) {
                PlanOps.hit(shown, w, worldTol(HIT_DP))?.let { GroupOps.member(project, it) }?.let { m ->
                    group = if (m in group) group - m else group + m
                }
            } else {
                selection = PlanOps.hit(shown, w, worldTol(HIT_DP))
            }
            Tool.WALL -> message = R.string.hint_wall
            Tool.MEASURE -> message = R.string.hint_measure
            Tool.TEXT -> labelRequest = LabelRequest(w, null)
            Tool.CALIBRATE -> message = R.string.hint_calibrate
            Tool.BACKGROUND -> Unit
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
            Tool.MEASURE -> {
                val s = snap(w)
                draft = Draft.MeasureDraft(s.point, s.point, s.type)
                DragOp.DrawMeasure(s.point)
            }
            Tool.TEXT -> {
                draft = Draft.ArrowDraft(w, w)
                DragOp.DrawArrow(w)
            }
            Tool.SELECT -> if (multiSelect) groupDragStart(w) else selectDragStart(screen, w)
            Tool.CALIBRATE -> {
                draft = Draft.MeasureDraft(w, w, SnapType.NONE)
                DragOp.DrawCalibration(w)
            }
            Tool.BACKGROUND -> DragOp.MoveBackground(project, w)
        }
    }

    private fun groupDragStart(w: P): DragOp {
        val hit = PlanOps.hit(shown, w, worldTol(HIT_DP))?.let { GroupOps.member(project, it) }
        if (hit != null && hit in group) return DragOp.MoveGroup(project, w)
        draft = Draft.BoxDraft(w, w)
        return DragOp.BoxSelect(w)
    }

    private fun selectDragStart(screen: Offset, w: P): DragOp {
        val tol = worldTol(HIT_DP)
        // Endpoint handles of the selected wall, rotation handle of the selected item.
        val sel = selection
        if (sel?.kind == Kind.ITEM) {
            val handle = itemRotateHandle(sel.id)
            if (handle != null && (handle - screen).getDistance() <= HANDLE_DP * density) {
                return DragOp.RotateItem(project, sel.id)
            }
        }
        if (sel?.kind == Kind.WALL) {
            project.wall(sel.id)?.let { wall ->
                val handleTol = worldTol(HANDLE_DP)
                if ((toScreen(wall.a) - toScreen(wall.b)).getDistance() > BEND_HANDLE_MIN_DP * density &&
                    Walls.apex(wall).dist(w) <= worldTol(HANDLE_DP * 0.7f) &&
                    minOf(wall.a.dist(w), wall.b.dist(w)) > handleTol
                ) {
                    return DragOp.BendWall(project, wall.id)
                }
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
        PlanOps.hitLabel(shown, w, tol)?.let { l ->
            selection = Selection(Kind.LABEL, l.id)
            return DragOp.MoveLabel(project, l.id, w)
        }
        Dimensions.hit(shown, w, tol)?.let { d ->
            selection = Selection(Kind.DIMENSION, d.id)
            return DragOp.MoveDimension(project, d.id)
        }
        PlanOps.hitItem(shown, w)?.let { item ->
            selection = Selection(Kind.ITEM, item.id)
            return DragOp.MoveItem(project, item.id, P(item.x, item.y) - w)
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
            is DragOp.BendWall -> {
                val wall = op.origin.wall(op.wallId) ?: return
                project = PlanOps.setWallBulge(op.origin, op.wallId, Walls.bulgeThrough(wall, w))
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
            is DragOp.DrawMeasure -> {
                val s = snap(w, start = op.start, exclude = op.start)
                draft = Draft.MeasureDraft(op.start, s.point, s.type)
            }
            is DragOp.DrawArrow -> draft = Draft.ArrowDraft(op.start, w)
            is DragOp.DrawCalibration -> draft = Draft.MeasureDraft(op.start, w, SnapType.NONE)
            is DragOp.MoveBackground -> {
                val bg = op.origin.background ?: return
                project = op.origin.copy(background = Backgrounds.move(bg, w - op.startWorld))
            }
            is DragOp.BoxSelect -> draft = Draft.BoxDraft(op.start, w)
            is DragOp.MoveGroup -> {
                val delta = w - op.startWorld
                val snapped = if (settings.snap.grid) Snapper.snapToGrid(delta, gridSize) else delta
                project = GroupOps.translate(op.origin, group, snapped)
            }
            is DragOp.MoveLabel -> project = PlanOps.moveLabel(op.origin, op.id, w - op.startWorld)
            is DragOp.MoveDimension -> {
                val d = op.origin.dimension(op.id) ?: return
                project = PlanOps.setDimensionOffset(op.origin, op.id, Dimensions.offsetThrough(d, w))
            }
            is DragOp.MoveItem -> {
                val item = op.origin.item(op.id) ?: return
                val raw = w + op.grab
                val center = if (settings.snap.grid) Snapper.snapToGrid(raw, ITEM_GRID) else raw
                val moved = (if (settings.snap.endpoints) Furniture.snapToWall(item, raw, op.origin.walls, worldTol(WALL_SNAP_DP)) else null)
                    ?: item.copy(x = roundTo(center.x, 1), y = roundTo(center.y, 1))
                project = PlanOps.updateItem(op.origin, op.id) { moved }
            }
            is DragOp.RotateItem -> {
                val item = op.origin.item(op.id) ?: return
                // The handle sits in front of the item (local +y), so the angle of the finger is rotation + 90.
                val deg = Math.toDegrees(kotlin.math.atan2(w.y - item.y, w.x - item.x)) - 90
                val snapped = if (settings.snap.angles) Math.round(deg / 15) * 15.0 else deg
                project = PlanOps.updateItem(op.origin, op.id) { it.copy(rotation = Furniture.normalizeAngle(snapped)) }
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
            is DragOp.BendWall -> commit(project, before = op.origin)
            is DragOp.MoveWall -> commit(project, before = op.origin)
            is DragOp.SlideOpening -> commit(project, before = op.origin)
            is DragOp.DrawMeasure -> {
                val d = draft as? Draft.MeasureDraft
                draft = null
                if (d != null) {
                    PlanOps.addDimension(project, d.a, d.b)?.let { (p, dim) ->
                        commit(p)
                        selection = Selection(Kind.DIMENSION, dim.id)
                    }
                }
            }
            is DragOp.MoveDimension -> commit(project, before = op.origin)
            is DragOp.MoveLabel -> commit(project, before = op.origin)
            is DragOp.MoveGroup -> commit(project, before = op.origin)
            is DragOp.BoxSelect -> {
                (draft as? Draft.BoxDraft)?.let { group = group + GroupOps.inBox(shown, it.a, it.b) }
                draft = null
            }
            is DragOp.DrawArrow -> {
                val d = draft as? Draft.ArrowDraft
                draft = null
                // The text goes where the drag started, the arrow points to where it ended.
                if (d != null) labelRequest = LabelRequest(d.a, d.b.takeIf { it.dist(d.a) > worldTol(HIT_DP) })
            }
            is DragOp.MoveItem -> commit(project, before = op.origin)
            is DragOp.RotateItem -> commit(project, before = op.origin)
            is DragOp.MoveBackground -> commit(project, before = op.origin)
            is DragOp.DrawCalibration -> {
                val d = draft as? Draft.MeasureDraft
                draft = null
                if (d != null && d.a.dist(d.b) > worldTol(HIT_DP)) calibrateRequest = d.a to d.b
            }
            is DragOp.Pan, null -> Unit
        }
        dragOp = null
    }

    fun onDragCancel() {
        when (val op = dragOp) {
            is DragOp.MoveVertex -> project = op.origin
            is DragOp.BendWall -> project = op.origin
            is DragOp.MoveWall -> project = op.origin
            is DragOp.SlideOpening -> project = op.origin
            is DragOp.MoveItem -> project = op.origin
            is DragOp.MoveDimension -> project = op.origin
            is DragOp.MoveLabel -> project = op.origin
            is DragOp.MoveGroup -> project = op.origin
            is DragOp.RotateItem -> project = op.origin
            is DragOp.MoveBackground -> project = op.origin
            else -> Unit
        }
        draft = null
        dragOp = null
    }

    private fun updateOpeningDraft(w: P, isDoor: Boolean) {
        val wall = PlanOps.hitWall(project, w, worldTol(OPENING_HIT_DP))?.takeUnless { it.isCurved }
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
        private const val WALL_SNAP_DP = 16f
        const val ROTATE_HANDLE_OFFSET_DP = 28f
        /** The bend handle shows on walls at least this long on screen. */
        private const val BEND_HANDLE_MIN_DP = 80f
        /** Furniture moves in 5 cm steps when grid snapping is on. */
        private const val ITEM_GRID = 5.0
        /** Longest side of a stored background photo, in pixels. */
        private const val BACKGROUND_MAX_PX = 2048
    }
}
