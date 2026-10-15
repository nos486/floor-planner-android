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
 * One finger: tap, or drag with the active tool (drag starts exactly where the finger went down). In the select
 * tool, holding the finger still first selects what is under it and then moves it.
 * Two fingers: pan and pinch-zoom; this cancels any one-finger drag in progress.
 */
suspend fun PointerInputScope.planGestures(vm: PlanViewModel) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var mode = Mode.UNDECIDED
        // In the select tool a finger held still picks up what is under it; see PlanViewModel.onHold.
        var holdPending = vm.holdSelects
        val holdAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        var now = down.uptimeMillis
        var event: PointerEvent
        do {
            if (holdPending && mode == Mode.UNDECIDED) {
                val next = withTimeoutOrNull(maxOf(1L, holdAt - now)) { awaitPointerEvent() }
                if (next == null) {
                    holdPending = false
                    if (vm.onHold(down.position)) mode = Mode.DRAG
                    event = awaitPointerEvent()
                } else {
                    event = next
                }
            } else {
                event = awaitPointerEvent()
            }
            now = event.changes.maxOf { it.uptimeMillis }
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
