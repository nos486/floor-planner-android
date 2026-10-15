package org.geekio.floorplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R

/** The views and checks of the plan, as large tiles that are easy to hit with a finger. */
enum class PlanTool(val icon: ImageVector, val label: Int) {
    VIEW_3D(Icons.Filled.ViewInAr, R.string.view_3d),
    SUNLIGHT(Icons.Filled.WbSunny, R.string.sunlight),
    CHECK(Icons.Filled.FactCheck, R.string.check_plan),
    MATERIALS(Icons.Filled.Calculate, R.string.materials),
    BUYER(Icons.Filled.House, R.string.home_buyer),
    TRUE_SCALE(Icons.Filled.Straighten, R.string.true_scale),
    LAYERS(Icons.Filled.Layers, R.string.layers),
    VERSIONS(Icons.Filled.History, R.string.versions),
    BACKGROUND(Icons.Filled.Image, R.string.background_photo),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(onPick: (PlanTool) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.tools),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PlanTool.entries) { t ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.clickable { onDismiss(); onPick(t) }.padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(t.icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(t.label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}
