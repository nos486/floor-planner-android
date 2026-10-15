package org.geekio.floorplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Estimates
import org.geekio.floorplanner.core.Units
import org.geekio.floorplanner.model.Costs
import java.util.Locale

/** Floor covering, paint and skirting quantities with optional unit prices. */
@Composable
fun EstimateDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    val p = vm.project
    val u = vm.settings.units
    var height by remember { mutableStateOf(p.wallHeight) }
    var waste by remember { mutableStateOf(p.costs.wastePercent) }
    // Prices are typed per displayed unit and kept per metric unit.
    var floorPrice by remember { mutableStateOf(priceText(p.costs.floorPerM2 / Units.areaFactor(u))) }
    var paintPrice by remember { mutableStateOf(priceText(p.costs.paintPerLiter / Units.volumeFactor(u))) }
    var skirtingPrice by remember { mutableStateOf(priceText(p.costs.skirtingPerM / Units.lengthFactor(u))) }

    val costs = Costs(
        floorPerM2 = (parseNumber(floorPrice) ?: 0.0) * Units.areaFactor(u),
        paintPerLiter = (parseNumber(paintPrice) ?: 0.0) * Units.volumeFactor(u),
        skirtingPerM = (parseNumber(skirtingPrice) ?: 0.0) * Units.lengthFactor(u),
        wastePercent = waste,
    )
    val preview = p.copy(wallHeight = height, costs = costs)
    val e = Estimates.estimate(preview)
    val c = Estimates.cost(e, preview)
    val multiFloor = p.floors.size > 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.materials)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stepper(stringResource(R.string.ceiling_height), height, 5.0, 200.0, 600.0, u) { height = it }
                LabeledRow(stringResource(R.string.waste)) {
                    IconButton(onClick = { waste = (waste - 5).coerceAtLeast(0.0) }, enabled = waste > 0) { Icon(Icons.Filled.Remove, null) }
                    Text(String.format(Locale.getDefault(), "%.0f%%", waste))
                    IconButton(onClick = { waste = (waste + 5).coerceAtMost(50.0) }, enabled = waste < 50) { Icon(Icons.Filled.Add, null) }
                }
                HorizontalDivider()
                MaterialRow(
                    stringResource(R.string.floor_covering), Units.area(e.coveringM2, u),
                    Units.areaUnit(u), floorPrice, { floorPrice = it }, c.floor,
                )
                MaterialRow(
                    stringResource(R.string.wall_paint), Units.volume(e.paintLiters, u) + "  ·  " + Units.area(e.wallM2, u),
                    Units.volumeUnit(u), paintPrice, { paintPrice = it }, c.paint,
                )
                MaterialRow(
                    stringResource(R.string.skirting), Units.length(e.skirtingM, u),
                    Units.lengthUnit(u), skirtingPrice, { skirtingPrice = it }, c.skirting,
                )
                if (c.total > 0) {
                    LabeledRow(stringResource(R.string.total_cost)) {
                        Text(Units.money(c.total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.by_room), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                for (r in e.rooms) {
                    val name = if (multiFloor) r.floorName + " · " + r.room.name else r.room.name
                    LabeledRow(name) {
                        Text(
                            stringResource(R.string.room_estimate, Units.area(r.floorM2, u), Units.area(r.wallM2, u)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    stringResource(R.string.estimate_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.setEstimateInputs(height, costs); onDismiss() }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun MaterialRow(label: String, quantity: String, unit: String, price: String, onPrice: (String) -> Unit, cost: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        LabeledRow(label) { Text(quantity, fontWeight = FontWeight.Medium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = price, onValueChange = onPrice, singleLine = true,
                label = { Text(stringResource(R.string.unit_price, unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(150.dp),
            )
            Text(
                if (cost > 0) Units.money(cost) else "",
                Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun priceText(v: Double) = if (v <= 0) "" else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
