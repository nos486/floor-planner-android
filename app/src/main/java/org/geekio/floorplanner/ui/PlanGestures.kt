package org.geekio.floorplanner.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope

/**
 * One finger: tap, or drag with the active tool (drag starts exactly where the finger went down).
 * Two fingers: pan and pinch-zoom; this cancels any one-finger drag in progress.
 */
suspend fun PointerInputScope.planGestures(vm: PlanViewModel) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var mode = Mode.UNDECIDED
        var event: PointerEvent
        do {
            event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                if (mode == Mode.DRAG) vm.onDragCancel()
                mode = Mode.TRANSFORM
                vm.onTransform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1 && mode != Mode.TRANSFORM) {
                val change = pressed[0]
                if (mode == Mode.UNDECIDED && (change.position - down.position).getDistance() > slop) {
                    mode = Mode.DRAG
                    vm.onDragStart(down.position)
                }
                if (mode == Mode.DRAG) {
                    vm.onDrag(change.position)
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })

        when (mode) {
            Mode.UNDECIDED -> vm.onTap(down.position)
            Mode.DRAG -> vm.onDragEnd()
            Mode.TRANSFORM -> Unit
        }
    }
}

private enum class Mode { UNDECIDED, DRAG, TRANSFORM }

/** Mouse wheel zoom around the cursor (emulator, Chromebook, desktop mode). */
suspend fun PointerInputScope.wheelZoom(vm: PlanViewModel) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.first()
                vm.onTransform(change.position, Offset.Zero, if (change.scrollDelta.y > 0) 0.9f else 1.1f)
                change.consume()
            }
        }
    }
}
