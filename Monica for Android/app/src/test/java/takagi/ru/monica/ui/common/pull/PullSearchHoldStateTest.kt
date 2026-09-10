package takagi.ru.monica.ui.common.pull

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PullSearchHoldStateTest {
    @Test
    fun opensAfterOneAndAHalfSecondsAndOnlyOncePerGesture() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(0, opens)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, opens)
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, opens)
    }

    @Test
    fun releasingEarlyCancelsTheHoldInsteadOfOpeningOnRelease() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_499)
        hold.endGesture()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, opens)
    }

    @Test
    fun cancellingBeforeTheCoroutineStartsCannotLeaveAPendingSearch() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        hold.endGesture()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, opens)
    }

    @Test
    fun scrollingContentDisqualifiesTheEntireGestureEvenAfterReachingTheTop() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.onScroll(contentConsumed = true)
        hold.onScroll(contentConsumed = false)
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(hold.canPull)
        assertEquals(0, opens)

        hold.endGesture()
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, opens)
    }

    @Test
    fun movingBelowThresholdOrIntoSyncRangeRequiresANewFullHold() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_000)
        hold.updatePull(inSearchRange = false)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, opens)

        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_499)
        runCurrent()
        assertEquals(0, opens)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, opens)
    }

    @Test
    fun smallMovementsWithinTheSearchRangeDoNotRestartTheTimer() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.onScroll()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(800)
        hold.onScroll()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(700)
        runCurrent()
        assertEquals(1, opens)
    }

    @Test
    fun aNewTouchCannotInheritTimeFromThePreviousPull() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_000)
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0, opens)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, opens)
    }

    @Test
    fun expandingSearchElsewherePreventsThePendingPullFromTriggering() = runTest {
        var expanded = false
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { !expanded }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_000)
        expanded = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, opens)
    }

    @Test
    fun consumingContentAfterTheTimerStartsCancelsIt() = runTest {
        var opens = 0
        val hold = PullSearchHoldState(backgroundScope, { true }) { opens++ }
        hold.beginGesture()
        hold.updatePull(inSearchRange = true)
        advanceTimeBy(1_000)
        hold.onScroll(contentConsumed = true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, opens)
    }
}
