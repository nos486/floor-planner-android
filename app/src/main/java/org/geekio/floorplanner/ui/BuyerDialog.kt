package org.geekio.floorplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Buyer
import org.geekio.floorplanner.core.BuyerSummary
import org.geekio.floorplanner.core.ProjectEntry
import org.geekio.floorplanner.core.UnitSystem
import org.geekio.floorplanner.core.Units
import java.util.Locale

/** Usable and deed-style area of the home, its price and the price per m². */
@Composable
fun BuyerDialog(vm: PlanViewModel, onDismiss: () -> Unit) {
    val u = vm.settings.units
    var priceText by remember { mutableStateOf(vm.project.price?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    val price = parsePrice(priceText)
    val s = Buyer.summary(vm.project.copy(price = price))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_buyer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it }, singleLine = true,
                    label = { Text(stringResource(R.string.price)) },
                    supportingText = { if (price != null) Text(Units.money(price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SummaryRows(s, u)
                HorizontalDivider()
                Text(stringResource(R.string.by_room), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                for (r in s.rooms) {
                    LabeledRow(r.name) {
                        Text(stringResource(R.string.room_net, Units.area(r.netM2, u), Units.area(r.areaM2, u)), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    stringResource(R.string.buyer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { vm.setPrice(price); onDismiss() }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun SummaryRows(s: BuyerSummary, u: UnitSystem) {
    LabeledRow(stringResource(R.string.net_area)) { Text(Units.area(s.netM2, u), fontWeight = FontWeight.Bold) }
    LabeledRow(stringResource(R.string.gross_area)) { Text(Units.area(s.grossM2, u), fontWeight = FontWeight.Medium) }
    s.pricePerNetM2?.let {
        LabeledRow(stringResource(R.string.price_per_net, Units.areaUnit(u))) { Text(Units.money(it / Units.areaFactor(u)), fontWeight = FontWeight.Bold) }
    }
    s.pricePerGrossM2?.let {
        LabeledRow(stringResource(R.string.price_per_gross, Units.areaUnit(u))) { Text(Units.money(it / Units.areaFactor(u))) }
    }
}

/** All saved homes side by side, cheapest per usable m² first; homes without a price go last. */
@Composable
fun CompareHomesDialog(projects: List<ProjectEntry>, u: UnitSystem, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val rows = remember(projects) {
        projects.map { it to Buyer.summary(it.project) }
            .sortedWith(compareBy({ it.second.pricePerNetM2 == null }, { it.second.pricePerNetM2 ?: 0.0 }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_homes)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rows.none { it.second.price != null }) {
                    Text(stringResource(R.string.compare_homes_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for ((entry, s) in rows) {
                    Column(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onDismiss(); onOpen(entry.id) }) {
                            Text(entry.project.projectName, style = MaterialTheme.typography.titleSmall)
                        }
                        s.price?.let { LabeledRow(stringResource(R.string.price)) { Text(Units.money(it)) } }
                        SummaryRows(s, u)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** A price typed with or without thousands separators, in Latin or Persian digits; null when empty or 0. */
fun parsePrice(text: String): Double? =
    Units.normalizeDigits(text.replace(",", "").replace("٬", "").replace(" ", "")).trim().toDoubleOrNull()?.takeIf { it > 0 }
