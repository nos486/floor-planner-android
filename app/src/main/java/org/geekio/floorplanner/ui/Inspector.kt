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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.geekio.floorplanner.core.Issue
import org.geekio.floorplanner.core.IssueKind
import org.geekio.floorplanner.core.Kind
import org.geekio.floorplanner.core.Layer
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.core.Walls
import org.geekio.floorplanner.model.Door

/** Bottom panel with the properties of the selected wall / door / window / room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Inspector(vm: PlanViewModel, openDialog: (ScreenDialog) -> Unit) {
    if (vm.multiSelect) {
        GroupPanel(vm)
        return
    }
    if (vm.checking && vm.selection == null) {
        IssuesPanel(vm)
        return
    }
    if (vm.sunlight && vm.selection == null) {
        SunPanel(vm, openDialog)
        return
    }
    val sel = vm.selection ?: return
    val p = vm.project
    val upm = p.scale.unitsPerMeter
    val u = vm.settings.units

    val title: String
    val body: @Composable () -> Unit
    when (sel.kind) {
        Kind.WALL -> {
            val wall = p.wall(sel.id) ?: return
            title = stringResource(R.string.wall)
            val lengthTitle = stringResource(R.string.length_m)
            body = {
                LabeledRow(stringResource(R.string.length)) {
                    if (wall.isCurved) {
                        Text(Units.length(Walls.length(wall) / upm, u), Modifier.padding(end = 12.dp))
                    } else {
                        TextButton(onClick = {
                            openDialog(ScreenDialog.Text(lengthTitle, Units.editValue(wall.length / upm, u)) { text ->
                                Units.parseLength(text, u)?.let { vm.setWallLengthMeters(wall.id, it) }
                            })
                        }) {
                            Text(Units.length(wall.length / upm, u))
                            Icon(Icons.Filled.Edit, null, Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Stepper(stringResource(R.string.curve), wall.bulge, 10.0, -wall.length / 2, wall.length / 2, u) { vm.setWallBulge(wall.id, it) }
                Stepper(stringResource(R.string.thickness), wall.thickness, 5.0, 5.0, 100.0, u) { vm.setWallThickness(wall.id, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = wall.height == null, onClick = { vm.setWallHeight(wall.id, null) },
                        label = { Text(stringResource(R.string.full_height)) },
                    )
                    FilterChip(
                        selected = wall.height != null, onClick = { vm.setWallHeight(wall.id, wall.height ?: 100.0) },
                        label = { Text(stringResource(R.string.low_wall)) },
                    )
                }
                wall.height?.let { h ->
                    Stepper(stringResource(R.string.wall_height), h, 10.0, 20.0, 250.0, u) { vm.setWallHeight(wall.id, it) }
                }
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
                Stepper(stringResource(R.string.width), door.width, 5.0, 30.0, 400.0, u) { vm.setOpeningWidth(Kind.DOOR, door.id, it) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((style, label) in listOf(null to R.string.door_single, Door.DOUBLE to R.string.door_double, Door.SLIDING to R.string.door_sliding)) {
                        FilterChip(
                            selected = door.style == style, onClick = { vm.setDoorStyle(door.id, style) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                if (door.style != Door.SLIDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.flipDoorSide(door.id) }) {
                            Icon(Icons.Filled.SwapVert, null)
                            Text(stringResource(R.string.flip_side), Modifier.padding(start = 8.dp))
                        }
                        if (door.style == null) {
                            OutlinedButton(onClick = { vm.flipDoorHinge(door.id) }) {
                                Icon(Icons.Filled.SwapHoriz, null)
                                Text(stringResource(R.string.flip_hinge), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        Kind.WINDOW -> {
            val win = p.window(sel.id) ?: return
            title = stringResource(R.string.window)
            body = {
                Stepper(stringResource(R.string.width), win.width, 10.0, 30.0, 1000.0, u) { vm.setOpeningWidth(Kind.WINDOW, win.id, it) }
            }
        }
        Kind.ITEM -> {
            val item = p.item(sel.id) ?: return
            title = stringResource(furnitureName(item.type))
            val angleTitle = stringResource(R.string.rotation_deg)
            body = {
                Stepper(stringResource(R.string.width), item.width, 5.0, 10.0, 1000.0, u) { vm.setItemSize(item.id, it, item.depth) }
                Stepper(stringResource(R.string.depth), item.depth, 5.0, 10.0, 1000.0, u) { vm.setItemSize(item.id, item.width, it) }
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
        Kind.LABEL -> {
            val l = p.label(sel.id) ?: return
            title = stringResource(R.string.label)
            val textTitle = stringResource(R.string.label_text)
            body = {
                LabeledRow(stringResource(R.string.label_text)) {
                    TextButton(onClick = {
                        openDialog(ScreenDialog.Text(textTitle, l.text) { vm.setLabelText(l.id, it) })
                    }) {
                        Text(l.text, maxLines = 1, modifier = Modifier.widthIn(max = 180.dp))
                        Icon(Icons.Filled.Edit, null, Modifier.padding(start = 6.dp))
                    }
                }
                Stepper(stringResource(R.string.text_size), l.size, 5.0, 5.0, 300.0, u) { vm.setLabelSize(l.id, it) }
                if (l.tip != null) {
                    OutlinedButton(onClick = { vm.removeLabelArrow(l.id) }) {
                        Text(stringResource(R.string.remove_arrow))
                    }
                }
            }
        }
        Kind.DIMENSION -> {
            val d = p.dimension(sel.id) ?: return
            title = stringResource(R.string.measurement)
            body = {
                LabeledRow(stringResource(R.string.length)) {
                    Text(Units.length(d.length / upm, u), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
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
                    Text(Units.area(room.areaM2, u), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
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
                // Tapping the title folds the panel away to free the plan for the finger.
                Row(
                    Modifier.weight(1f).clickable { vm.inspectorCollapsed = !vm.inspectorCollapsed }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        if (vm.inspectorCollapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        stringResource(if (vm.inspectorCollapsed) R.string.expand else R.string.collapse),
                        Modifier.padding(start = 4.dp),
                    )
                }
                if (sel.kind != Kind.ROOM) {
                    IconButton(onClick = vm::deleteSelected) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            if (!vm.inspectorCollapsed) body()
        }
    }
}

/** Actions for everything in the multiple selection. */
@Composable
private fun GroupPanel(vm: PlanViewModel) {
    val n = vm.group.size
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (n == 0) stringResource(R.string.group_hint) else stringResource(R.string.group_count, n),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::selectAll) { Text(stringResource(R.string.select_all)) }
                IconButton(onClick = { vm.enableMultiSelect(false) }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::duplicateGroup, enabled = n > 0) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Text(stringResource(R.string.duplicate), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = vm::rotateGroup, enabled = n > 0) {
                    Icon(Icons.Filled.Rotate90DegreesCw, null)
                    Text(stringResource(R.string.rotate), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = vm::mirrorGroup, enabled = n > 0) {
                    Icon(Icons.Filled.Flip, null)
                    Text(stringResource(R.string.mirror), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = vm::deleteGroup, enabled = n > 0) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.delete), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The problems found by the plan check; tapping one moves the view to it. */
@Composable
private fun IssuesPanel(vm: PlanViewModel) {
    val issues = vm.issues
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (issues.isEmpty()) stringResource(R.string.msg_no_issues) else stringResource(R.string.issues_count, issues.size),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::stopChecking) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                for (issue in issues) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.focusIssue(issue) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WarningAmber, null, tint = ISSUE_COLOR, modifier = Modifier.padding(end = 12.dp))
                        Text(issueText(issue, vm.project.scale.unitsPerMeter, vm.settings.units), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun issueText(issue: Issue, upm: Double, u: UnitSystem): String {
    val subject = if (issue.kind in listOf(IssueKind.NARROW_ROOM, IssueKind.NO_DOOR, IssueKind.NO_WINDOW)) issue.subject
    else stringResource(furnitureName(issue.subject))
    return when (issue.kind) {
        IssueKind.DOOR_BLOCKED -> stringResource(R.string.issue_door_blocked, subject)
        IssueKind.ITEMS_OVERLAP -> stringResource(R.string.issue_overlap, subject, stringResource(furnitureName(issue.other)))
        IssueKind.ITEM_IN_WALL -> stringResource(R.string.issue_in_wall, subject)
        IssueKind.WINDOW_BLOCKED -> stringResource(R.string.issue_window_blocked, subject)
        IssueKind.NARROW_ROOM -> stringResource(R.string.issue_narrow, subject, Units.length(issue.value / upm, u))
        IssueKind.NO_DOOR -> stringResource(R.string.issue_no_door, subject)
        IssueKind.NO_WINDOW -> stringResource(R.string.issue_no_window, subject)
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
fun Stepper(
    label: String, value: Double, step: Double, min: Double, max: Double,
    units: UnitSystem = UnitSystem.METRIC, onChange: (Double) -> Unit,
) {
    LabeledRow(label) {
        IconButton(onClick = { onChange((value - step).coerceIn(min, max)) }, enabled = value > min) {
            Icon(Icons.Filled.Remove, null)
        }
        Text(Units.small(value, units), Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(min, max)) }, enabled = value < max) {
            Icon(Icons.Filled.Add, null)
        }
    }
}

private fun fmtPlain(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledRow(stringResource(R.string.units)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = s.units == UnitSystem.METRIC,
                            onClick = { vm.updateSettings(s.copy(units = UnitSystem.METRIC)) },
                            label = { Text(stringResource(R.string.units_metric)) },
                        )
                        FilterChip(
                            selected = s.units == UnitSystem.IMPERIAL,
                            onClick = { vm.updateSettings(s.copy(units = UnitSystem.IMPERIAL)) },
                            label = { Text(stringResource(R.string.units_imperial)) },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.show_grid), s.showGrid) { vm.updateSettings(s.copy(showGrid = it)) }
                SwitchRow(stringResource(R.string.show_overall), s.showOverall) { vm.updateSettings(s.copy(showOverall = it)) }
                SwitchRow(stringResource(R.string.snap_grid), s.snap.grid) { vm.updateSettings(s.copy(snap = s.snap.copy(grid = it))) }
                SwitchRow(stringResource(R.string.snap_endpoints), s.snap.endpoints) { vm.updateSettings(s.copy(snap = s.snap.copy(endpoints = it))) }
                SwitchRow(stringResource(R.string.snap_angles), s.snap.angles) { vm.updateSettings(s.copy(snap = s.snap.copy(angles = it))) }
                Stepper(stringResource(R.string.grid_size), vm.project.scale.gridCm, 5.0, 5.0, 200.0, s.units) { vm.setGridCm(it) }
                Stepper(stringResource(R.string.default_thickness), s.wallThickness, 5.0, 5.0, 100.0, s.units) { vm.updateSettings(s.copy(wallThickness = it)) }
                Stepper(stringResource(R.string.default_door), s.doorWidth, 5.0, 30.0, 400.0, s.units) { vm.updateSettings(s.copy(doorWidth = it)) }
                Stepper(stringResource(R.string.default_window), s.windowWidth, 10.0, 30.0, 1000.0, s.units) { vm.updateSettings(s.copy(windowWidth = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun BackgroundDialog(vm: PlanViewModel, onPick: () -> Unit, onDismiss: () -> Unit) {
    val bg = vm.project.background ?: return
    var opacity by remember { mutableStateOf(bg.opacity.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.background_photo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.backgroundImage == null) Text(stringResource(R.string.background_missing))
                Text(stringResource(R.string.opacity), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f,
                    onValueChangeFinished = { vm.setBackgroundOpacity(opacity.toDouble()) },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDismiss(); vm.selectTool(Tool.CALIBRATE) }) { Text(stringResource(R.string.set_scale)) }
                    OutlinedButton(onClick = { onDismiss(); vm.selectTool(Tool.BACKGROUND) }) { Text(stringResource(R.string.move_photo)) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDismiss(); onPick() }) { Text(stringResource(R.string.replace_photo)) }
                    OutlinedButton(onClick = { onDismiss(); vm.removeBackground() }) {
                        Text(stringResource(R.string.remove_photo), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun LayersDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layers)) },
        text = {
            Column {
                for (layer in Layer.entries) {
                    SwitchRow(stringResource(layerName(layer)), layer !in s.hiddenLayers) { on ->
                        vm.updateSettings(s.copy(hiddenLayers = if (on) s.hiddenLayers - layer else s.hiddenLayers + layer))
                    }
                }
                Text(
                    stringResource(R.string.layers_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

fun layerName(l: Layer): Int = when (l) {
    Layer.FURNITURE -> R.string.layer_furniture
    Layer.ELECTRICAL -> R.string.cat_electrical
    Layer.PLUMBING -> R.string.cat_plumbing
    Layer.DIMENSIONS -> R.string.layer_dimensions
    Layer.LABELS -> R.string.layer_labels
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledRow(label) { Switch(checked = checked, onCheckedChange = onChange) }
}
