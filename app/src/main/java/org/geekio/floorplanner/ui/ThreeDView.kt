package org.geekio.floorplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.geekio.floorplanner.R
import org.geekio.floorplanner.core.Camera
import org.geekio.floorplanner.core.FaceKind
import org.geekio.floorplanner.core.Scene3D
import org.geekio.floorplanner.model.Project

/** Full-screen 3D model of the open floor: drag to turn around it, pinch to zoom. */
@Composable
fun ThreeDView(project: Project, onDismiss: () -> Unit) {
    val (target, startDistance) = remember(project) { Scene3D.frame(project) }
    var yaw by remember { mutableStateOf(-Math.PI / 2 - 0.6) }
    var pitch by remember { mutableStateOf(0.65) }
    var distance by remember { mutableStateOf(startDistance) }
    var low by remember { mutableStateOf(false) }
    val faces = remember(project, low) { Scene3D.split(Scene3D.build(project, if (low) LOW_WALLS else project.wallHeight)) }
    val focus = remember(target, project.wallHeight) { target.copy(z = project.wallHeight * 0.25) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFE9EEF3)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            yaw -= pan.x * 0.008
                            pitch = (pitch + pan.y * 0.006).coerceIn(0.08, 1.5)
                            distance = (distance / zoom).coerceIn(150.0, 30000.0)
                        }
                    }
                ) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFDCE7F2), Color(0xFFF4F1EC))))
                    val cam = Camera(focus, yaw, pitch, distance, focal = size.minDimension * 1.2)
                    val center = Offset(size.width / 2, size.height / 2)
                    val order = Scene3D.paintOrder(faces, cam)
                    for ((f, pts) in order) {
                        val path = Path().apply {
                            moveTo(center.x + pts[0].x.toFloat(), center.y + pts[0].y.toFloat())
                            for (i in 1 until pts.size) lineTo(center.x + pts[i].x.toFloat(), center.y + pts[i].y.toFloat())
                            close()
                        }
                        val base = Color(f.color.toInt())
                        val k = if (f.kind == FaceKind.FLOOR || f.kind == FaceKind.RUG || f.kind == FaceKind.GLASS) 1f else cam.shade(f.points).toFloat()
                        drawPath(path, Color(base.red * k, base.green * k, base.blue * k, base.alpha))
                        if (f.kind != FaceKind.GLASS) drawPath(path, Color(0x22000000), style = Stroke(1f))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color(0xFF1F2937)) }
                    Text(
                        stringResource(R.string.view_3d), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F2937),
                    )
                    FilterChip(
                        selected = low, onClick = { low = !low },
                        label = { Text(stringResource(R.string.low_walls)) },
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color.White),
                    )
                }
                Text(
                    stringResource(R.string.hint_3d),
                    Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563),
                )
            }
        }
    }
}

/** Wall height of the cut-away view, low enough to look into the rooms. */
private const val LOW_WALLS = 110.0
