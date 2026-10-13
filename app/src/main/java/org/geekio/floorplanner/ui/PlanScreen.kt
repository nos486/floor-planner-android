package org.geekio.floorplanner.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.ProjectIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: PlanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ScreenDialog?>(null) }

    vm.message?.let { msgRes ->
        val text = stringResource(msgRes)
        LaunchedEffect(msgRes, vm.message) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
            vm.message = null
        }
    }

    fun write(uri: android.net.Uri?, content: String) {
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
    val saveSvg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) {
        write(it, vm.exportSvg())
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            }
            if (!vm.loadJson(text)) vm.message = R.string.msg_open_failed
        }
    }

    BackHandler(enabled = vm.selection != null) { vm.clearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { dialog = ScreenDialog.RenameProject }) {
                        Text(vm.project.projectName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.total_area, fmt(vm.project.totalAreaM2), vm.project.rooms.size),
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
                    IconButton(onClick = vm::fitToScreen) {
                        Icon(Icons.Filled.FitScreen, stringResource(R.string.fit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.new_project)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.NewProject
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.open_json)) }, onClick = {
                                menuOpen = false; openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_json)) }, onClick = {
                                menuOpen = false; saveJson.launch(ProjectIO.fileName(vm.project, "json"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.export_svg)) }, onClick = {
                                menuOpen = false; saveSvg.launch(ProjectIO.fileName(vm.project, "svg"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                                menuOpen = false; dialog = ScreenDialog.Settings
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
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PlanCanvas(vm, Modifier.fillMaxSize())
            if (vm.project.walls.isEmpty() && vm.tool == Tool.WALL) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(stringResource(R.string.hint_start), Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }

    when (val d = dialog) {
        ScreenDialog.RenameProject -> TextInputDialog(
            title = stringResource(R.string.project_name), initial = vm.project.projectName,
            onDismiss = { dialog = null }, onConfirm = { vm.renameProject(it); dialog = null },
        )
        ScreenDialog.NewProject -> TextInputDialog(
            title = stringResource(R.string.new_project), initial = stringResource(R.string.default_project_name),
            message = stringResource(R.string.new_project_warning),
            onDismiss = { dialog = null }, onConfirm = { vm.newProject(it); dialog = null },
        )
        ScreenDialog.Settings -> SettingsDialog(vm) { dialog = null }
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
fun parseNumber(text: String): Double? {
    val normalized = text.trim().map { ch ->
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("")
    return normalized.toDoubleOrNull()
}

@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        content()
    }
}
