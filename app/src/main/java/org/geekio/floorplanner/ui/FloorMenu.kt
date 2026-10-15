package org.geekio.floorplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Floors

/** A chip with the open floor's name; its menu switches, adds, renames and deletes floors. */
@Composable
fun FloorMenu(vm: PlanViewModel, openDialog: (ScreenDialog) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val p = vm.project
    val firstName = stringResource(R.string.floor_n, 1)
    val floors = Floors.all(p, firstName)
    val current = p.floorIndex.coerceIn(0, floors.lastIndex)
    val newName = stringResource(R.string.floor_n, floors.size + 1)
    val renameTitle = stringResource(R.string.floor_name)
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(floors[current].name.ifBlank { firstName }) },
            leadingIcon = { Icon(Icons.Filled.Layers, null, Modifier.size(AssistChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) },
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // Top floor first, like a building.
            for (i in floors.indices.reversed()) {
                DropdownMenuItem(
                    text = { Text(floors[i].name.ifBlank { firstName }) },
                    leadingIcon = { if (i == current) Icon(Icons.Filled.Check, null) },
                    onClick = { open = false; vm.switchFloor(i) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_floor)) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { open = false; vm.addFloor(newName, firstName) },
            )
            if (p.floors.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_floor)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = {
                        open = false
                        openDialog(ScreenDialog.Text(renameTitle, floors[current].name) { vm.renameFloor(current, it) })
                    },
                )
            }
            if (floors.size > 1) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_floor)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { open = false; vm.deleteFloor(current) },
                )
            }
        }
    }
}
