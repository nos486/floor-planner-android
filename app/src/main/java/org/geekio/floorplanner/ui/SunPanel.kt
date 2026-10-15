package org.geekio.floorplanner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/** Month and time sliders for the sunlight view, the north arrow and latitude, and sun hours per room. */
@Composable
fun SunPanel(vm: PlanViewModel, openDialog: (ScreenDialog) -> Unit) {
    val p = vm.project
    val sun = vm.sunPosition
    val latitudeTitle = stringResource(R.string.latitude)
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sunlight), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (sun.isUp) stringResource(R.string.sun_height, sun.elevation.roundToInt()) else stringResource(R.string.sun_down),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = vm::stopSunlight) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
            val months = DateFormatSymbols.getInstance().months
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(months[vm.sunMonth - 1], Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = vm.sunMonth.toFloat(), onValueChange = { vm.setSunTime(it.roundToInt(), vm.sunHour) },
                    valueRange = 1f..12f, steps = 10, modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val h = vm.sunHour
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", h.toInt(), ((h - h.toInt()) * 60).roundToInt()),
                    Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = h.toFloat(), onValueChange = { vm.setSunTime(vm.sunMonth, (it * 4).roundToInt() / 4.0) },
                    valueRange = 4f..21f, modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.north), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.rotateNorth(-15.0) }) { Icon(Icons.Filled.Rotate90DegreesCcw, null) }
                Text("${p.north.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.rotateNorth(15.0) }) { Icon(Icons.Filled.Rotate90DegreesCw, null) }
                TextButton(onClick = {
                    openDialog(ScreenDialog.Text(latitudeTitle, String.format(Locale.US, "%.1f", p.latitude)) { text ->
                        text.replace(',', '.').trim().toDoubleOrNull()?.let(vm::setLatitude)
                    })
                }) {
                    Text(stringResource(R.string.latitude_value, String.format(Locale.getDefault(), "%.1f°", p.latitude)))
                    Icon(Icons.Filled.Edit, null, Modifier.padding(start = 6.dp))
                }
            }
            val hours = vm.roomSun
            if (hours.isNotEmpty()) {
                Text(stringResource(R.string.sun_hours_title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in hours) {
                        AssistChip(onClick = {}, label = {
                            Text(stringResource(R.string.sun_hours, r.name, String.format(Locale.getDefault(), "%.1f", r.hours)))
                        })
                    }
                }
            }
        }
    }
}
