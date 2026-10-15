package org.geekio.floorplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Furniture
import org.geekio.floorplanner.core.FurnitureCategory
import org.geekio.floorplanner.core.FurnitureType

/** Bottom sheet with the furniture catalog (and the extra drawing tools). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: PlanViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "tools") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolChip(Icons.Filled.Straighten, R.string.tool_measure) {
                        vm.selectTool(Tool.MEASURE)
                        onDismiss()
                    }
                }
            }
            for (category in FurnitureCategory.entries) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$category") {
                    Text(
                        stringResource(categoryName(category)),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                for (type in Furniture.catalog.filter { it.category == category }) {
                    item(key = type.key) {
                        CatalogCell(type) {
                            vm.addFurniture(type)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(icon: ImageVector, label: Int, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
private fun CatalogCell(type: FurnitureType, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val item = Furniture.create(type, org.geekio.floorplanner.core.P.ZERO)
        Canvas(Modifier.size(64.dp)) {
            val k = minOf(size.width / item.width.toFloat(), size.height / item.depth.toFloat()) * 0.9f
            translate(size.width / 2, size.height / 2) {
                scale(k, pivot = Offset.Zero) {
                    drawItem(item, cs.surfaceContainerHighest, cs.onSurface.copy(alpha = 0.75f), 1.5f / k)
                }
            }
        }
        Text(
            stringResource(furnitureName(type.key)),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
