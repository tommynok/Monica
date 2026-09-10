package takagi.ru.monica.ui.common.pull

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SEARCH_HOLD_MILLIS = 1_500L

/** A search belongs to one uninterrupted pull that did not scroll list content. */
internal class PullSearchHoldState(
    private val scope: CoroutineScope,
    private val canTriggerSearch: () -> Boolean,
    private val onSearchTriggered: () -> Unit,
) {
    var isGestureActive = false
        private set
    private var scrolledContent = false
    private var triggered = false
    private var holdJob: Job? = null

    val canPull: Boolean get() = isGestureActive && !scrolledContent

    fun beginGesture() {
        cancelHold()
        isGestureActive = true
        scrolledContent = false
        triggered = false
    }

    fun onScroll(contentConsumed: Boolean = false) {
        if (!isGestureActive) beginGesture()
        if (contentConsumed) {
            scrolledContent = true
            cancelHold()
        }
    }

    fun updatePull(inSearchRange: Boolean) {
        if (!inSearchRange || !isGestureActive || scrolledContent || !canTriggerSearch()) {
            cancelHold()
            return
        }
        if (triggered || holdJob != null) return
        holdJob = scope.launch {
            delay(SEARCH_HOLD_MILLIS)
            if (isGestureActive && !scrolledContent && !triggered && canTriggerSearch()) {
                triggered = true
                onSearchTriggered()
            }
        }
    }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
    }

    fun endGesture() {
        isGestureActive = false
        cancelHold()
    }
}

/** Observe touch lifetime without consuming events or competing with the child scrollable. */
internal fun Modifier.observePullSearchGesture(hold: PullSearchHoldState): Modifier =
    pointerInput(hold) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            hold.beginGesture()
            try {
                while (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) {
                    // The nested scroll connection owns the distance and content-scroll checks.
                }
            } finally {
                // Also cancels a pending hold when Android cancels a touch without flinging.
                hold.endGesture()
            }
        }
    }
