package org.geekio.floorplanner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Dxf
import org.geekio.floorplanner.core.Layer
import org.geekio.floorplanner.core.ProjectIO
import org.geekio.floorplanner.core.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

const val PRIVACY_POLICY_URL = "https://github.com/nos486/floor-planner-android/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: PlanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ScreenDialog?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    vm.message?.let { msgRes ->
        val text = stringResource(msgRes)
        LaunchedEffect(msgRes, vm.message) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
            vm.message = null
        }
    }

    fun write(uri: Uri?, content: String) {
        uri ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(content) }
                }.isSuccess
            }
            vm.message = if (ok) R.string.msg_saved else R.string.msg_save_failed
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        write(it, vm.exportJson())
    }
    val saveDxf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/dxf")) {
        write(it, Dxf.export(vm.shown, vm.settings.units, Layer.DIMENSIONS !in vm.settings.hiddenLayers))
    }
    val saveSvg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) {
        write(it, ProjectIO.toSvg(vm.shown, vm.settings.units, Layer.DIMENSIONS !in vm.settings.hiddenLayers))
    }

    fun saveBytes(uri: Uri?, render: () -> ByteArray) {
        uri ?: return
        scope.launch {
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = render()
                    context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }
                }.isSuccess
            }
            vm.message = if (ok) R.string.msg_saved else R.string.msg_save_failed
        }
    }

    // Exports leave out hidden layers.
    val project = vm.shown
    val settings = vm.settings
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        saveBytes(it) { PlanExport.png(context, project, settings) }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        saveBytes(it) { PlanExport.pdf(context, project, settings) }
    }

    fun share(ext: String, mime: String, render: () -> ByteArray) {
        scope.launch {
            val file = withContext(Dispatchers.Default) {
                runCatching {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                    File(dir, ProjectIO.fileName(project, ext)).also { it.writeBytes(render()) }
                }.getOrNull()
            }
            if (file == null) {
                vm.message = R.string.msg_save_failed
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, project.projectName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(send, null))
            } catch (e: ActivityNotFoundException) {
                vm.message = R.string.msg_no_share_app
            }
        }
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.setBackground(it) }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            }
            if (!vm.importJson(text)) vm.message = R.string.msg_open_failed
        }
    }

    BackHandler {
        when {
            vm.multiSelect -> vm.enableMultiSelect(false)
            vm.selection != null -> vm.clearSelection()
            else -> vm.closeProject()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = vm::closeProject) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.my_projects))
                    }
                },
                title = {
                    Column(Modifier.clickable { dialog = ScreenDialog.RenameProject }) {
                        Text(vm.project.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.total_area, Units.area(vm.project.totalAreaM2, vm.settings.units), vm.project.rooms.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = vm::redo, enabled = vm.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo))
                    }
                    IconButton(onClick = { dialog = ScreenDialog.View3D }) {
                        Icon(Icons.Filled.ViewInAr, stringResource(R.string.view_3d))
                    }
                    IconButton(onClick = vm::fitToScreen) {
                        Icon(Icons.Filled.FitScreen, stringResource(R.string.fit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.my_projects)) }, onClick = {
                                menuOpen = false; vm.closeProject()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.new_project)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.NewProject
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.open_json)) }, onClick = {
                                menuOpen = false; openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_json)) }, onClick = {
                                menuOpen = false; saveJson.launch(ProjectIO.fileName(vm.project, "json"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Share
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Export
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.background_photo)) }, onClick = {
                                menuOpen = false
                                if (vm.project.background == null) pickBackground.launch("image/*") else dialog = ScreenDialog.Background
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.versions)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Versions
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.true_scale)) }, onClick = {
                                menuOpen = false; vm.showTrueScale()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.layers)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Layers
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.check_plan)) }, onClick = {
                                menuOpen = false; vm.checkPlan()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sunlight)) }, onClick = {
                                menuOpen = false; vm.showSunlight()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.materials)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Materials
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.home_buyer)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Buyer
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Settings
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.privacy_policy)) }, onClick = {
                                menuOpen = false
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                                } catch (e: ActivityNotFoundException) {
                                    vm.message = R.string.msg_no_browser
                                }
                            })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Inspector(vm) { dialog = it }
                NavigationBar {
                    ToolItem(vm, Tool.SELECT, Icons.Filled.NearMe, R.string.tool_select)
                    ToolItem(vm, Tool.WALL, Icons.Filled.Timeline, R.string.tool_wall)
                    ToolItem(vm, Tool.DOOR, Icons.Filled.DoorFront, R.string.tool_door)
                    ToolItem(vm, Tool.WINDOW, Icons.Filled.Window, R.string.tool_window)
                    // The extra tools picked from the Add sheet show up in its place.
                    val (addIcon, addLabel) = when (vm.tool) {
                        Tool.MEASURE -> Icons.Filled.Straighten to R.string.tool_measure
                        Tool.TEXT -> Icons.Filled.TextFields to R.string.tool_text
                        else -> Icons.Filled.Chair to R.string.tool_add
                    }
                    NavigationBarItem(
                        selected = vm.tool == Tool.MEASURE || vm.tool == Tool.TEXT,
                        onClick = { addOpen = true },
                        icon = { Icon(addIcon, null) },
                        label = { Text(stringResource(addLabel)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PlanCanvas(vm, Modifier.fillMaxSize())
            if (vm.tool == Tool.SELECT && vm.project.walls.isNotEmpty()) {
                FilterChip(
                    selected = vm.multiSelect,
                    onClick = { vm.enableMultiSelect(!vm.multiSelect) },
                    label = { Text(stringResource(R.string.select_several)) },
                    leadingIcon = { Icon(Icons.Filled.HighlightAlt, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            if (vm.isTrueScale) {
                AssistChip(
                    onClick = vm::fitToScreen,
                    label = { Text(stringResource(R.string.true_scale_on)) },
                    leadingIcon = { Icon(Icons.Filled.Straighten, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(AssistChipDefaults.IconSize)) },
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
            CompareBanner(vm, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            if (vm.tool == Tool.CALIBRATE || vm.tool == Tool.BACKGROUND) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (vm.tool == Tool.CALIBRATE) R.string.hint_calibrate else R.string.hint_move_background),
                            Modifier.weight(1f, fill = false),
                        )
                        TextButton(onClick = { vm.selectTool(Tool.SELECT) }) { Text(stringResource(R.string.done)) }
                    }
                }
            }
            if (!vm.multiSelect) {
                FloorMenu(vm, { dialog = it }, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (vm.project.walls.isEmpty() && vm.tool == Tool.WALL) {
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(stringResource(R.string.hint_start), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }

    if (addOpen) AddSheet(vm) { addOpen = false }

    vm.calibrateRequest?.let {
        TextInputDialog(
            title = stringResource(R.string.calibrate_title) + " (" + Units.lengthUnit(vm.settings.units) + ")",
            message = stringResource(R.string.calibrate_message),
            initial = "",
            keyboardType = if (vm.settings.units == org.geekio.floorplanner.core.UnitSystem.METRIC) KeyboardType.Decimal else KeyboardType.Text,
            onDismiss = { vm.calibrateRequest = null },
            onConfirm = { text -> Units.parseLength(text, vm.settings.units)?.let { vm.calibrate(it) } ?: run { vm.calibrateRequest = null } },
        )
    }

    vm.labelRequest?.let {
        TextInputDialog(
            title = stringResource(R.string.label_text), initial = "",
            onDismiss = { vm.labelRequest = null }, onConfirm = { text -> vm.addLabel(text) },
        )
    }

    when (val d = dialog) {
        ScreenDialog.RenameProject -> TextInputDialog(
            title = stringResource(R.string.project_name), initial = vm.project.projectName,
            onDismiss = { dialog = null }, onConfirm = { vm.renameProject(it); dialog = null },
        )
        ScreenDialog.NewProject -> NewProjectDialog(vm.settings.units, onDismiss = { dialog = null }) { p ->
            dialog = null
            vm.createProject(p.copy(scale = vm.project.scale))
        }
        ScreenDialog.Settings -> SettingsDialog(vm) { dialog = null }
        ScreenDialog.Materials -> EstimateDialog(vm) { dialog = null }
        ScreenDialog.Buyer -> BuyerDialog(vm) { dialog = null }
        ScreenDialog.Layers -> LayersDialog(vm) { dialog = null }
        ScreenDialog.Versions -> VersionsDialog(vm) { dialog = null }
        ScreenDialog.Background -> BackgroundDialog(vm, onPick = { pickBackground.launch("image/*") }) { dialog = null }
        ScreenDialog.View3D -> ThreeDView(vm.project) { dialog = null }
        ScreenDialog.Share -> ChoiceDialog(
            stringResource(R.string.share),
            listOf(
                stringResource(R.string.format_png) to { share("png", "image/png") { PlanExport.png(context, project, settings) } },
                stringResource(R.string.format_pdf) to { share("pdf", "application/pdf") { PlanExport.pdf(context, project, settings) } },
            ),
        ) { dialog = null }
        ScreenDialog.Export -> ChoiceDialog(
            stringResource(R.string.export),
            listOf(
                stringResource(R.string.format_png) to { savePng.launch(ProjectIO.fileName(project, "png")) },
                stringResource(R.string.format_pdf) to { savePdf.launch(ProjectIO.fileName(project, "pdf")) },
                stringResource(R.string.format_svg) to { saveSvg.launch(ProjectIO.fileName(project, "svg")) },
                stringResource(R.string.format_dxf) to { saveDxf.launch(ProjectIO.fileName(project, "dxf")) },
            ),
        ) { dialog = null }
        is ScreenDialog.Number -> TextInputDialog(
            title = d.title, initial = d.initial, keyboardType = KeyboardType.Decimal,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                parseNumber(text)?.let(d.onValue)
                dialog = null
            },
        )
        is ScreenDialog.Text -> TextInputDialog(
            title = d.title, initial = d.initial,
            onDismiss = { dialog = null }, onConfirm = { d.onValue(it); dialog = null },
        )
        null -> Unit
    }
}

sealed interface ScreenDialog {
    data object RenameProject : ScreenDialog
    data object NewProject : ScreenDialog
    data object Settings : ScreenDialog
    data object Materials : ScreenDialog
    data object Buyer : ScreenDialog
    data object Layers : ScreenDialog
    data object Versions : ScreenDialog
    data object Background : ScreenDialog
    data object View3D : ScreenDialog
    data object Share : ScreenDialog
    data object Export : ScreenDialog
    class Number(val title: String, val initial: String, val onValue: (Double) -> Unit) : ScreenDialog
    class Text(val title: String, val initial: String, val onValue: (String) -> Unit) : ScreenDialog
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ToolItem(vm: PlanViewModel, tool: Tool, icon: ImageVector, label: Int) {
    NavigationBarItem(
        selected = vm.tool == tool,
        onClick = { vm.selectTool(tool) },
        icon = { Icon(icon, null) },
        label = { Text(stringResource(label)) },
    )
}

/** A dialog with one row per option; picking a row runs it and closes the dialog. */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                for ((label, action) in options) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onDismiss(); action() }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    message: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message)
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** Formats with the device locale (Persian digits on Persian devices). */
fun fmt(v: Double, decimals: Int = 2): String = String.format(Locale.getDefault(), "%.${decimals}f", v)

/** Accepts Latin, Persian and Arabic digits and either decimal separator. */
fun parseNumber(text: String): Double? = Units.normalizeDigits(text).trim().toDoubleOrNull()

@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        content()
    }
}
