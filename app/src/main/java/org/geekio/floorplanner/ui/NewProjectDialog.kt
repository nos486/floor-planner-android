package org.geekio.floorplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.RoomKey
import org.geekio.floorplanner.core.Templates
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.model.Project

/** Asks for the name of a new project and what to start from: an empty plan or a template. */
@Composable
fun NewProjectDialog(units: UnitSystem, onDismiss: () -> Unit, onCreate: (Project) -> Unit) {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.default_project_name)
    var name by remember { mutableStateOf(defaultName) }
    var template by remember { mutableStateOf<String?>(null) }
    // Templates are built once, with localized room names, for their previews.
    val previews = remember {
        Templates.all.associate { t -> t.key to Templates.build(t.key, "") { context.getString(roomKeyName(it)) }!! }
    }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_project)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.project_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.start_from), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                val options = listOf<Pair<String?, Project?>>(null to null) + Templates.all.map { it.key to previews[it.key] }
                for ((key, preview) in options) {
                    val selected = template == key
                    OutlinedCard(
                        onClick = { template = key },
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 72.dp, height = 52.dp)) {
                                if (preview != null) {
                                    Canvas(Modifier.size(width = 72.dp, height = 52.dp)) {
                                        drawThumbnail(preview, cs.onSurface.copy(alpha = 0.8f), cs.primary.copy(alpha = 0.15f))
                                    }
                                }
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                val t = Templates.all.firstOrNull { it.key == key }
                                Text(stringResource(templateName(key)), style = MaterialTheme.typography.bodyLarge)
                                if (t != null) {
                                    Text(
                                        stringResource(R.string.template_info, Units.area(t.area.toDouble(), units), t.rooms),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalName = name.trim().ifEmpty { defaultName }
                val p = template?.let { previews[it]?.copy(projectName = finalName) } ?: Project(projectName = finalName)
                onCreate(p)
            }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

fun templateName(key: String?): Int = when (key) {
    "studio" -> R.string.tpl_studio
    "one_bed" -> R.string.tpl_one_bed
    "two_bed" -> R.string.tpl_two_bed
    "three_bed" -> R.string.tpl_three_bed
    else -> R.string.tpl_blank
}

fun roomKeyName(key: RoomKey): Int = when (key) {
    RoomKey.LIVING -> R.string.rk_living
    RoomKey.KITCHEN -> R.string.rk_kitchen
    RoomKey.BEDROOM -> R.string.rk_bedroom
    RoomKey.MASTER_BEDROOM -> R.string.rk_master_bedroom
    RoomKey.BATHROOM -> R.string.rk_bathroom
    RoomKey.WC -> R.string.rk_wc
    RoomKey.STORAGE -> R.string.rk_storage
    RoomKey.STUDY -> R.string.rk_study
    RoomKey.FAMILY -> R.string.rk_family
}
