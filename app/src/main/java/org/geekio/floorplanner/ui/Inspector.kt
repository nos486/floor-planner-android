package org.geekio.floorplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.FloorPatterns
import org.geekio.floorplanner.core.Kind

/** Bottom panel with the properties of the selected wall / door / window / room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inspector(vm: PlanViewModel, openDialog: (ScreenDialog) -> Unit) {
    val sel = vm.selection ?: return
    val p = vm.project
    val upm = p.scale.unitsPerMeter

    val title: String
    val body: @Composable () -> Unit
    when (sel.kind) {
        Kind.WALL -> {
            val wall = p.wall(sel.id) ?: return
            title = stringResource(R.string.wall)
            val lengthTitle = stringResource(R.string.length_m)
            body = {
                LabeledRow(stringResource(R.string.length)) {
                    TextButton(onClick = {
                        openDialog(ScreenDialog.Number(lengthTitle, fmtPlain(wall.length / upm)) { vm.setWallLengthMeters(wall.id, it) })
                    }) {
                        Text(fmt(wall.length / upm) + " m")
                        Icon(Icons.Filled.Edit, null, Modifier.padding(start = 6.dp))
                    }
                }
                Stepper(stringResource(R.string.thickness), wall.thickness, 5.0, 5.0, 100.0) { vm.setWallThickness(wall.id, it) }
                OutlinedButton(onClick = { vm.splitWall(wall.id) }) {
                    Icon(Icons.Filled.ContentCut, null)
                    Text(stringResource(R.string.split_wall), Modifier.padding(start = 8.dp))
                }
            }
        }
        Kind.DOOR -> {
            val door = p.door(sel.id) ?: return
            title = stringResource(R.string.door)
            body = {
                Stepper(stringResource(R.string.width), door.width, 5.0, 30.0, 400.0) { vm.setOpeningWidth(Kind.DOOR, door.id, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.flipDoorSide(door.id) }) {
                        Icon(Icons.Filled.SwapVert, null)
                        Text(stringResource(R.string.flip_side), Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { vm.flipDoorHinge(door.id) }) {
                        Icon(Icons.Filled.SwapHoriz, null)
                        Text(stringResource(R.string.flip_hinge), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        Kind.WINDOW -> {
            val win = p.window(sel.id) ?: return
            title = stringResource(R.string.window)
            body = {
                Stepper(stringResource(R.string.width), win.width, 10.0, 30.0, 1000.0) { vm.setOpeningWidth(Kind.WINDOW, win.id, it) }
            }
        }
        Kind.ITEM -> {
            val item = p.item(sel.id) ?: return
            title = stringResource(furnitureName(item.type))
            val angleTitle = stringResource(R.string.rotation_deg)
            body = {
                Stepper(stringResource(R.string.width), item.width, 5.0, 10.0, 1000.0) { vm.setItemSize(item.id, it, item.depth) }
                Stepper(stringResource(R.string.depth), item.depth, 5.0, 10.0, 1000.0) { vm.setItemSize(item.id, item.width, it) }
                LabeledRow(stringResource(R.string.rotation)) {
                    IconButton(onClick = { vm.rotateItem(item.id, -90.0) }) { Icon(Icons.Filled.Rotate90DegreesCcw, null) }
                    TextButton(onClick = {
                        openDialog(ScreenDialog.Number(angleTitle, fmtPlain(item.rotation)) { vm.setItemRotation(item.id, it) })
                    }) { Text(fmt(item.rotation, 0) + "°") }
                    IconButton(onClick = { vm.rotateItem(item.id, 90.0) }) { Icon(Icons.Filled.Rotate90DegreesCw, null) }
                }
                OutlinedButton(onClick = { vm.duplicateItem(item.id) }) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Text(stringResource(R.string.duplicate), Modifier.padding(start = 8.dp))
                }
            }
        }
        Kind.DIMENSION -> {
            val d = p.dimension(sel.id) ?: return
            title = stringResource(R.string.measurement)
            body = {
                LabeledRow(stringResource(R.string.length)) {
                    Text(fmt(d.length / upm) + " m", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                }
                Text(
                    stringResource(R.string.hint_move_dimension),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Kind.ROOM -> {
            val room = p.room(sel.id) ?: return
            title = stringResource(R.string.room)
            val renameTitle = stringResource(R.string.room_name)
            body = {
                LabeledRow(stringResource(R.string.name)) {
                    TextButton(onClick = {
                        openDialog(ScreenDialog.Text(renameTitle, room.name) { vm.renameRoom(room.id, it) })
                    }) {
                        Text(room.name)
                        Icon(Icons.Filled.Edit, null, Modifier.padding(start = 6.dp))
                    }
                }
                LabeledRow(stringResource(R.string.area)) {
                    Text(fmt(room.areaM2) + " m²", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Swatch(null, room.color == null) { vm.setRoomColor(room.id, null) }
                    for (hex in FloorPatterns.swatches) {
                        Swatch(hex, room.color == hex) { vm.setRoomColor(room.id, hex) }
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (m in listOf<String?>(null) + FloorPatterns.materials) {
                        FilterChip(
                            selected = room.floor == m,
                            onClick = { vm.setRoomFloor(room.id, m) },
                            label = { Text(stringResource(materialName(m))) },
                        )
                    }
                }
            }
        }
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (sel.kind != Kind.ROOM) {
                    IconButton(onClick = vm::deleteSelected) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            body()
        }
    }
}

/** A round colour button; [hex] null is the automatic colour. */
@Composable
private fun Swatch(hex: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val color = FloorPatterns.parseColor(hex)?.let { Color(it) }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color ?: cs.surfaceVariant)
            .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Icon(Icons.Filled.FormatColorReset, stringResource(R.string.color_auto), Modifier.size(18.dp))
    }
}

fun materialName(key: String?): Int = when (key) {
    "wood" -> R.string.mat_wood
    "tile" -> R.string.mat_tile
    "mosaic" -> R.string.mat_mosaic
    "stone" -> R.string.mat_stone
    "carpet" -> R.string.mat_carpet
    else -> R.string.mat_none
}

/** A "label  [-] value cm [+]" row. */
@Composable
fun Stepper(label: String, value: Double, step: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    LabeledRow(label) {
        IconButton(onClick = { onChange((value - step).coerceIn(min, max)) }, enabled = value > min) {
            Icon(Icons.Filled.Remove, null)
        }
        Text(fmt(value, 0) + " cm", Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(min, max)) }, enabled = value < max) {
            Icon(Icons.Filled.Add, null)
        }
    }
}

private fun fmtPlain(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

@Composable
fun SettingsDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(stringResource(R.string.show_grid), s.showGrid) { vm.updateSettings(s.copy(showGrid = it)) }
                SwitchRow(stringResource(R.string.show_overall), s.showOverall) { vm.updateSettings(s.copy(showOverall = it)) }
                SwitchRow(stringResource(R.string.snap_grid), s.snap.grid) { vm.updateSettings(s.copy(snap = s.snap.copy(grid = it))) }
                SwitchRow(stringResource(R.string.snap_endpoints), s.snap.endpoints) { vm.updateSettings(s.copy(snap = s.snap.copy(endpoints = it))) }
                SwitchRow(stringResource(R.string.snap_angles), s.snap.angles) { vm.updateSettings(s.copy(snap = s.snap.copy(angles = it))) }
                Stepper(stringResource(R.string.grid_size), vm.project.scale.gridCm, 5.0, 5.0, 200.0) { vm.setGridCm(it) }
                Stepper(stringResource(R.string.default_thickness), s.wallThickness, 5.0, 5.0, 100.0) { vm.updateSettings(s.copy(wallThickness = it)) }
                Stepper(stringResource(R.string.default_door), s.doorWidth, 5.0, 30.0, 400.0) { vm.updateSettings(s.copy(doorWidth = it)) }
                Stepper(stringResource(R.string.default_window), s.windowWidth, 10.0, 30.0, 1000.0) { vm.updateSettings(s.copy(windowWidth = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledRow(label) { Switch(checked = checked, onCheckedChange = onChange) }
}
