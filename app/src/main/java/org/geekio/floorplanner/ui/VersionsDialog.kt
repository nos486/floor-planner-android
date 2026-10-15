package org.geekio.floorplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Units
import java.text.DateFormat
import java.util.Date

/** Saved versions of the plan: save the current one, compare with one, restore or delete it. */
@Composable
fun VersionsDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshVersions() }
    var name by remember { mutableStateOf("") }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.versions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.versions_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.version_name)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.saveVersion(name); name = "" }, enabled = name.isNotBlank()) {
                        Text(stringResource(R.string.save_version))
                    }
                }
                HorizontalDivider()
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (vm.versions.isEmpty()) {
                        Text(stringResource(R.string.no_versions), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (v in vm.versions) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.name, fontWeight = FontWeight.Medium)
                                Text(format.format(Date(v.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.compareWith(v); onDismiss() }) {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, stringResource(R.string.compare))
                            }
                            IconButton(onClick = { vm.restoreVersion(v.key); onDismiss() }) {
                                Icon(Icons.Filled.Restore, stringResource(R.string.restore))
                            }
                            IconButton(onClick = { vm.deleteVersion(v.key) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Banner shown while comparing with a saved version, with the area before and now and the rooms that changed. */
@Composable
fun CompareBanner(vm: PlanViewModel, modifier: Modifier = Modifier) {
    val (name, _) = vm.comparing ?: return
    val d = vm.diff ?: return
    val u = vm.settings.units
    var open by remember { mutableStateOf(false) }
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 2.dp) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(stringResource(R.string.comparing_with, name), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.compare_areas, Units.area(d.areaBefore, u), Units.area(d.areaAfter, u)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { open = !open }) { Text(stringResource(R.string.details)) }
                IconButton(onClick = vm::stopComparing) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            if (open) {
                Text(stringResource(R.string.compare_legend), style = MaterialTheme.typography.bodySmall)
                for (r in d.changedRooms) {
                    Text(
                        r.name + ": " + (r.before?.let { Units.area(it, u) } ?: "–") + " → " + (r.after?.let { Units.area(it, u) } ?: "–"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
