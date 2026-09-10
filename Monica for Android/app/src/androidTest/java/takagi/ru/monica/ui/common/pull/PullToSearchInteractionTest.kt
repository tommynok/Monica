package takagi.ru.monica.ui.common.pull

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PullToSearchInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)

    @After
    fun resumeClockForDisposal() {
        compose.mainClock.autoAdvance = true
    }

    private fun show(initialIndex: Int = 0, empty: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                val pull = rememberPullToSearchState(
                    isSearchExpanded = expanded,
                    searchTriggerDistance = with(density) { 72.dp.toPx() },
                    maxDragDistance = with(density) { 100.dp.toPx() },
                    onSearchTriggered = { expanded = true },
                )
                Column(Modifier.fillMaxSize()) {
                    Text(if (expanded) "Search open" else "Search closed")
                    val listModifier = Modifier.fillMaxSize()
                        .offset { IntOffset(0, pull.currentOffset.toInt()) }
                        .then(pull.gestureModifier)
                        .testTag("search-list")
                    if (empty) {
                        Box(listModifier.pointerInput(expanded) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, amount -> pull.onVerticalDrag(amount) },
                                onDragEnd = pull.onDragEnd,
                                onDragCancel = pull.onDragCancel,
                            )
                        })
                    } else {
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
                        // Platform stretch rendering uses a different clock from these hold tests.
                        LazyColumn(modifier = listModifier, state = listState, overscrollEffect = null) {
                            items(40, key = { it }) { index ->
                                Text("Item $index", Modifier.fillMaxWidth().height(72.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun pullDown() {
        compose.onNodeWithTag("search-list").performTouchInput {
            down(Offset(centerX, height * 0.15f))
            moveTo(Offset(centerX, height * 0.75f), delayMillis = 200)
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun release() {
        compose.onNodeWithTag("search-list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
    }

    @Test
    fun quickPullAtTheTopDoesNotOpenSearchOnRelease() {
        show()
        pullDown()
        compose.mainClock.advanceTimeBy(500)
        release()
        compose.runOnIdle { assertFalse(expanded) }
    }

    @Test
    fun holdingAPullAtTheTopOpensSearchBeforeRelease() {
        show()
        pullDown()
        compose.mainClock.advanceTimeBy(1_800)
        compose.runOnIdle { assertTrue(expanded) }
        release()
    }

    @Test
    fun scrollingBackToTheTopAndHoldingDoesNotOpenSearch() {
        show(initialIndex = 1)
        pullDown()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertFalse(expanded) }
        release()
    }

    @Test
    fun aCancelledTouchDoesNotOpenSearchLater() {
        show()
        pullDown()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("search-list").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertFalse(expanded) }
    }

    @Test
    fun anEmptyListUsesTheSameHoldRequirement() {
        show(empty = true)
        pullDown()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(expanded) }
        compose.mainClock.advanceTimeBy(1_300)
        compose.runOnIdle { assertTrue(expanded) }
        release()
    }
}
