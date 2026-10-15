package org.geekio.floorplanner.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Kind
import kotlin.math.roundToInt

/** Quick actions floating just above the selected thing, so they are under the thumb instead of in the bottom panel. */
@Composable
fun SelectionBar(vm: PlanViewModel) {
    val sel = vm.selection ?: return
    // Rooms can't be deleted on their own; their settings stay in the bottom panel.
    if (sel.kind == Kind.ROOM) return
    val anchor = vm.selectionAnchor() ?: return
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gap = with(LocalDensity.current) { 26.dp.roundToPx() }
    val actions = buildList<Triple<ImageVector, Int, () -> Unit>> {
        when (sel.kind) {
            Kind.ITEM -> {
                val locked = vm.project.item(sel.id)?.locked == true
                if (!locked) add(Triple(Icons.Filled.Rotate90DegreesCw, R.string.rotate) { vm.rotateItem(sel.id, 90.0) })
                add(Triple(Icons.Filled.ContentCopy, R.string.duplicate) { vm.duplicateItem(sel.id) })
                add(
                    if (locked) Triple(Icons.Filled.Lock, R.string.unlock_item) { vm.toggleItemLock(sel.id) }
                    else Triple(Icons.Filled.LockOpen, R.string.lock_item) { vm.toggleItemLock(sel.id) },
                )
            }
            Kind.WALL -> add(Triple(Icons.Filled.ContentCut, R.string.split_wall) { vm.splitWall(sel.id) })
            Kind.DOOR -> {
                add(Triple(Icons.Filled.SwapVert, R.string.flip_side) { vm.flipDoorSide(sel.id) })
                add(Triple(Icons.Filled.SwapHoriz, R.string.flip_hinge) { vm.flipDoorHinge(sel.id) })
            }
            else -> Unit
        }
        add(Triple(Icons.Filled.Delete, R.string.delete) { vm.deleteSelected() })
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .onSizeChanged { size = it }
            .offset {
                val maxX = maxOf(0, vm.viewSize.width - size.width)
                val x = (anchor.x - size.width / 2f).roundToInt().coerceIn(0, maxX)
                // Above the selection, or below its top edge when there is no room above.
                val above = anchor.y.roundToInt() - size.height - gap
                IntOffset(x, if (above >= 0) above else anchor.y.roundToInt() + gap)
            },
    ) {
        Row(Modifier.padding(horizontal = 4.dp)) {
            for ((icon, label, action) in actions) {
                BarButton(icon, stringResource(label), danger = label == R.string.delete, onClick = action)
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, danger: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}
