package org.geekio.floorplanner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Floors
import org.geekio.floorplanner.core.ProjectEntry
import org.geekio.floorplanner.core.ProjectIO
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Start screen: the list of saved projects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PlanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    vm.message?.let { msgRes ->
        val text = stringResource(msgRes)
        LaunchedEffect(msgRes, vm.message) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
            vm.message = null
        }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_projects)) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.open_json)) }, onClick = {
                                menuOpen = false
                                openJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.sample_plan)) }, onClick = {
                                menuOpen = false; vm.openSample()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.compare_homes)) }, onClick = {
                                menuOpen = false; dialog = HomeDialog.Compare
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                                menuOpen = false; dialog = HomeDialog.Settings
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = HomeDialog.New },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_project)) },
            )
        },
    ) { padding ->
        if (vm.projects.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_projects), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.no_projects_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(onClick = vm::openSample, modifier = Modifier.padding(top = 20.dp)) {
                    Text(stringResource(R.string.open_sample_plan))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.projects, key = { it.id }) { entry ->
                    ProjectCard(
                        entry, vm.settings.units,
                        onOpen = { vm.openProject(entry.id) },
                        onRename = { dialog = HomeDialog.Rename(entry) },
                        onDuplicate = { dialog = HomeDialog.Duplicate(entry) },
                        onDelete = { dialog = HomeDialog.Delete(entry) },
                    )
                }
            }
        }
    }

    val copySuffix = stringResource(R.string.copy_suffix)
    when (val d = dialog) {
        HomeDialog.New -> NewProjectDialog(vm.settings.units, onDismiss = { dialog = null }) { p ->
            dialog = null
            vm.createProject(p)
        }
        HomeDialog.Settings -> SettingsDialog(vm) { dialog = null }
        HomeDialog.Compare -> CompareHomesDialog(vm.projects, vm.settings.units, onOpen = vm::openProject) { dialog = null }
        is HomeDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.project_name), initial = d.entry.project.projectName,
            onDismiss = { dialog = null }, onConfirm = { vm.renameProjectEntry(d.entry.id, it); dialog = null },
        )
        is HomeDialog.Duplicate -> TextInputDialog(
            title = stringResource(R.string.duplicate), initial = d.entry.project.projectName + " " + copySuffix,
            onDismiss = { dialog = null }, onConfirm = { vm.duplicateProject(d.entry.id, it); dialog = null },
        )
        is HomeDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete_project)) },
            text = { Text(stringResource(R.string.delete_project_warning, d.entry.project.projectName)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(d.entry.id); dialog = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
        null -> Unit
    }
}

private sealed interface HomeDialog {
    data object New : HomeDialog
    data object Settings : HomeDialog
    data object Compare : HomeDialog
    class Rename(val entry: ProjectEntry) : HomeDialog
    class Duplicate(val entry: ProjectEntry) : HomeDialog
    class Delete(val entry: ProjectEntry) : HomeDialog
}

@Composable
private fun ProjectCard(entry: ProjectEntry, units: UnitSystem, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f).padding(10.dp)) {
            drawThumbnail(entry.project, cs.onSurface.copy(alpha = 0.8f), cs.primary.copy(alpha = 0.15f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 10.dp)) {
                Text(entry.project.projectName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Units.area(Floors.totalAreaM2(entry.project), units) + " · " +
                        DateUtils.getRelativeTimeSpanString(entry.modified).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** A small preview of [p]: room fills and walls, fitted into the canvas. */
fun DrawScope.drawThumbnail(p: Project, wall: Color, room: Color) {
    val pts = ProjectIO.extentPoints(p)
    if (pts.isEmpty()) return
    val minX = pts.minOf { it.x }
    val minY = pts.minOf { it.y }
    val w = maxOf(pts.maxOf { it.x } - minX, 50.0)
    val h = maxOf(pts.maxOf { it.y } - minY, 50.0)
    val k = minOf(size.width / w, size.height / h).toFloat()
    val ox = (size.width - w.toFloat() * k) / 2
    val oy = (size.height - h.toFloat() * k) / 2
    fun o(x: Double, y: Double) = Offset(ox + ((x - minX) * k).toFloat(), oy + ((y - minY) * k).toFloat())
    for (r in p.rooms) {
        val poly = r.polygon
        if (poly.size < 3) continue
        val path = Path().apply {
            val first = o(poly[0].x, poly[0].y)
            moveTo(first.x, first.y)
            poly.drop(1).forEach { val q = o(it.x, it.y); lineTo(q.x, q.y) }
            close()
        }
        drawPath(path, room)
    }
    for (wl in p.walls) {
        drawLine(wall, o(wl.a.x, wl.a.y), o(wl.b.x, wl.b.y), maxOf(1.5f, (wl.thickness * k).toFloat()), cap = StrokeCap.Square)
    }
}
