package takagi.ru.monica.ui.common.pull

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import takagi.ru.monica.ui.haptic.rememberHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Stable
data class PullToSearchStateHandle(
    val currentOffset: Float,
    val nestedScrollConnection: NestedScrollConnection,
    val gestureModifier: Modifier,
    val onVerticalDrag: (Float) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit
)

@Composable
fun rememberPullToSearchState(
    isSearchExpanded: Boolean,
    searchTriggerDistance: Float,
    maxDragDistance: Float,
    onSearchTriggered: () -> Unit
): PullToSearchStateHandle {
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val onSearchTriggeredState by rememberUpdatedState(onSearchTriggered)
    var currentOffset by remember { mutableFloatStateOf(0f) }
    val searchExpandedState by rememberUpdatedState(isSearchExpanded)
    val collapseAnimatable = remember { Animatable(0f) }
    val hold = remember(scope) {
        PullSearchHoldState(
            scope = scope,
            canTriggerSearch = { !searchExpandedState },
            onSearchTriggered = {
                haptic.performPullThreshold()
                onSearchTriggeredState()
            },
        )
    }

    fun updateOffset(newOffset: Float) {
        currentOffset = newOffset
        hold.updatePull(currentOffset >= searchTriggerDistance)
    }

    fun interruptCollapseAnimation() {
        if (!collapseAnimatable.isRunning) return
        scope.launch {
            collapseAnimatable.stop()
            collapseAnimatable.snapTo(currentOffset)
        }
    }

    suspend fun collapsePullOffsetSmoothly() {
        if (currentOffset <= 0.5f) {
            currentOffset = 0f
            return
        }
        if (collapseAnimatable.isRunning) return
        collapseAnimatable.snapTo(currentOffset)
        try {
            collapseAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 140,
                    easing = FastOutLinearInEasing
                )
            ) {
                currentOffset = value
            }
        } finally {
            currentOffset = 0f
            collapseAnimatable.snapTo(0f)
        }
    }

    fun endGesture() {
        hold.endGesture()
    }

    fun onVerticalDrag(dragAmount: Float) {
        if (searchExpandedState) return
        hold.onScroll()
        interruptCollapseAnimation()
        if (dragAmount < 0f) {
            updateOffset((currentOffset + dragAmount).coerceAtLeast(0f))
            return
        }
        if (dragAmount == 0f) return
        updateOffset(
            calculateDampedPullOffset(
                currentOffset = currentOffset,
                dragDelta = dragAmount,
                maxDragDistance = maxDragDistance
            )
        )
    }

    val onDragEnd: () -> Unit = {
        endGesture()
        scope.launch {
            collapsePullOffsetSmoothly()
        }
    }
    val onDragCancel: () -> Unit = {
        endGesture()
        scope.launch { collapsePullOffsetSmoothly() }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            hold.cancelHold()
            collapsePullOffsetSmoothly()
        }
    }

    val nestedScrollConnection = remember(searchTriggerDistance, maxDragDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !searchExpandedState) hold.onScroll()
                if (currentOffset > 0f && available.y < 0f) {
                    interruptCollapseAnimation()
                    val newOffset = (currentOffset + available.y).coerceAtLeast(0f)
                    val consumed = currentOffset - newOffset
                    updateOffset(newOffset)
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || searchExpandedState) return Offset.Zero
                hold.onScroll(contentConsumed = consumed.y != 0f)
                if (
                    available.y > 0f &&
                    hold.canPull
                ) {
                    interruptCollapseAnimation()
                    updateOffset(
                        calculateDampedPullOffset(
                            currentOffset = currentOffset,
                            dragDelta = available.y,
                            maxDragDistance = maxDragDistance
                        )
                    )
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                endGesture()
                collapsePullOffsetSmoothly()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!hold.isGestureActive && currentOffset > 0f) {
                    collapsePullOffsetSmoothly()
                }
                return Velocity.Zero
            }
        }
    }

    return PullToSearchStateHandle(
        currentOffset = currentOffset,
        nestedScrollConnection = nestedScrollConnection,
        gestureModifier = Modifier.observePullSearchGesture(hold).nestedScroll(nestedScrollConnection),
        onVerticalDrag = ::onVerticalDrag,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel
    )
}
