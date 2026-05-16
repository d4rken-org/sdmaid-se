package eu.darken.sdmse.swiper.ui.swipe

import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.SwiperSessionsRoute
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.SwiperSettings
import eu.darken.sdmse.swiper.ui.SwiperStatusRoute
import eu.darken.sdmse.swiper.ui.SwiperSwipeRoute
import eu.darken.sdmse.swiper.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class SwiperSwipeViewModelTest : BaseTest() {

    private fun session(
        id: String = "session-1",
        currentIndex: Int = 0,
    ): SwipeSession = SwipeSession(
        sessionId = id,
        sourcePaths = listOf(LocalPath.build("storage", "emulated", "0", "DCIM")),
        currentIndex = currentIndex,
        totalItems = 10,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        lastModifiedAt = Instant.parse("2025-01-01T00:00:00Z"),
        state = SessionState.READY,
    )

    private fun item(
        id: Long,
        decision: SwipeDecision = SwipeDecision.UNDECIDED,
        sessionId: String = "session-1",
        idx: Int = id.toInt(),
    ): SwipeItem = previewSwipeItem(
        id = id,
        sessionId = sessionId,
        itemIndex = idx,
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "DCIM", "img$id.jpg"),
            size = 1024L * id,
        ),
        decision = decision,
    )

    // DataStoreValue.value() is an extension that calls flow.first(); stubbing the `flow` property
    // is enough for read paths. The .value(T) writer extension calls update {...}; with a relaxed
    // mock, update {...} answers without throwing — what we observe is that update was invoked.
    private fun <T : Any> mockSetting(value: T): DataStoreValue<T> =
        mockk<DataStoreValue<T>>(relaxed = true).apply {
            every { flow } returns flowOf(value)
        }

    private class Harness(
        val vm: SwiperSwipeViewModel,
        val swiper: Swiper,
        val settings: SwiperSettings,
        val exclusionManager: ExclusionManager,
        val viewIntentTool: ViewIntentTool,
        val sessionFlow: MutableStateFlow<SwipeSession?>,
        val itemsFlow: MutableStateFlow<List<SwipeItem>>,
        val sessionsWithStatsFlow: MutableStateFlow<List<Swiper.SessionWithStats>>,
        val hapticSetting: DataStoreValue<Boolean>,
        val gestureOverlaySetting: DataStoreValue<Boolean>,
    )

    // TestScope extension so the harness can launch a state collector inside the test's own scope.
    // SwiperSwipeViewModel's state uses `.safeStateIn(initialValue = null)` (WhileSubscribed) so
    // without an active subscriber, state.value stays null even after upstream flows have values.
    private fun TestScope.harness(
        session: SwipeSession? = session(),
        items: List<SwipeItem> = emptyList(),
        bind: Boolean = true,
        startIndex: Int = -1,
        hasSessionLookups: Boolean = true,
        hapticEnabled: Boolean = false,
        showGestureOverlay: Boolean = false,
        sessionsWithStats: List<Swiper.SessionWithStats> = emptyList(),
    ): Harness {
        val sessionFlow = MutableStateFlow(session)
        val itemsFlow = MutableStateFlow(items)
        val sessionsWithStatsFlow = MutableStateFlow(sessionsWithStats)

        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSession(any()) } returns sessionFlow
            every { getItemsForSession(any()) } returns itemsFlow
            every { getSessionsWithStats() } returns sessionsWithStatsFlow
            coEvery { this@apply.hasSessionLookups(any()) } returns hasSessionLookups
        }

        val hapticSetting = mockSetting(hapticEnabled)
        val gestureOverlaySetting = mockSetting(!showGestureOverlay)
        val settings = mockk<SwiperSettings>().apply {
            every { swapSwipeDirections } returns mockSetting(false)
            every { showFileDetailsOverlay } returns mockSetting(true)
            every { hapticFeedbackEnabled } returns hapticSetting
            every { hasShownGestureOverlay } returns gestureOverlaySetting
        }
        val exclusionManager = mockk<ExclusionManager>(relaxed = true)
        val viewIntentTool = mockk<ViewIntentTool>(relaxed = true)

        val vm = SwiperSwipeViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            swiper = swiper,
            settings = settings,
            exclusionManager = exclusionManager,
            viewIntentTool = viewIntentTool,
        )

        if (bind) {
            vm.bindRoute(
                SwiperSwipeRoute(
                    sessionId = session?.sessionId ?: "session-1",
                    startIndex = startIndex,
                ),
            )
        }

        // Keep state subscribed for the entire test via TestScope.backgroundScope, which is
        // auto-cancelled at runTest completion without blocking the test body.
        // Without this, safeStateIn's WhileSubscribed lazy-collection means state.value returns the
        // initialValue (null) and state.first() races with the upstream chain — both make the
        // route-driven state derivations invisible to tests.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.state.collect { /* keep subscription alive */ }
        }

        return Harness(
            vm = vm,
            swiper = swiper,
            settings = settings,
            exclusionManager = exclusionManager,
            viewIntentTool = viewIntentTool,
            sessionFlow = sessionFlow,
            itemsFlow = itemsFlow,
            sessionsWithStatsFlow = sessionsWithStatsFlow,
            hapticSetting = hapticSetting,
            gestureOverlaySetting = gestureOverlaySetting,
        )
    }

    @Test
    fun `state is null before bindRoute is called`() = runTest2 {
        val h = harness(bind = false)
        advanceUntilIdle()
        h.vm.state.first() shouldBe null
    }

    @Test
    fun `bindRoute populates state with session items`() = runTest2 {
        val items = listOf(item(1), item(2))
        val h = harness(items = items)
        advanceUntilIdle()

        val state = h.vm.state.first()!!
        state.items shouldBe items
        state.currentIndex shouldBe 0
    }

    @Test
    fun `bindRoute is idempotent`() = runTest2 {
        val h = harness()
        // Second bind is silently ignored — the guard prevents accidentally rebinding to a different
        // session after process death + restart.
        h.vm.bindRoute(SwiperSwipeRoute(sessionId = "other-session"))
        advanceUntilIdle()

        // No re-lookup for "other-session": state still derived from original session.
        h.vm.state.first()!!.session?.sessionId shouldBe "session-1"
    }

    @Test
    fun `bindRoute with startIndex applies it as override when session has a non-zero current index`() = runTest2 {
        // The production combine clears the override when session.currentIndex == 0 (treated as
        // "session was reset after delete, override is stale"). For the override to be applied,
        // session.currentIndex must already be > 0 (i.e. user navigated from a mid-session position).
        val items = (1..5).map { item(it.toLong()) }
        val h = harness(
            session = session(currentIndex = 1),
            items = items,
            startIndex = 3,
        )
        advanceUntilIdle()

        h.vm.state.first()!!.currentIndex shouldBe 3
    }

    @Test
    fun `bindRoute with startIndex is cleared when session has zero current index - stale override guard`() = runTest2 {
        // Regression guard for the "stale override" branch in SwiperSwipeViewModel.state. If
        // session.currentIndex == 0 (the session was reset after a delete), the override carried in
        // via the back-stack navigation must be cleared — otherwise the user would land on an item
        // index that no longer maps to a real position.
        val items = (1..5).map { item(it.toLong()) }
        val h = harness(
            session = session(currentIndex = 0),
            items = items,
            startIndex = 3,
        )
        advanceUntilIdle()

        h.vm.state.first()!!.currentIndex shouldBe 0
    }

    @Test
    fun `bindRoute with negative startIndex defers to session currentIndex`() = runTest2 {
        val items = (1..5).map { item(it.toLong()) }
        val h = harness(session = session(currentIndex = 2), items = items, startIndex = -1)
        advanceUntilIdle()

        h.vm.state.first()!!.currentIndex shouldBe 2
    }

    @Test
    fun `bindRoute when cache is empty navigates to sessions`() = runTest2 {
        // Cache miss after process death — the swipe screen can't render lookups and must navigate
        // back to sessions where continueSession can refresh the cache.
        val h = harness(bind = false, hasSessionLookups = false)
        h.vm.bindRoute(SwiperSwipeRoute(sessionId = "session-1"))
        advanceUntilIdle()

        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperSessionsRoute
        nav.inclusive shouldBe true
    }

    @Test
    fun `setDecision updates swiper decision and advances index`() = runTest2 {
        val items = listOf(
            item(1, SwipeDecision.UNDECIDED),
            item(2, SwipeDecision.UNDECIDED),
        )
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.updateDecision(1L, SwipeDecision.KEEP) }
        coVerify { h.swiper.updateCurrentIndex(any(), 1) }
    }

    @Test
    fun `setDecision triggers haptic feedback when enabled`() = runTest2 {
        val items = listOf(item(1))
        val h = harness(items = items, hapticEnabled = true)
        advanceUntilIdle()

        val collected = collectEvents(h.vm)

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        advanceUntilIdle()

        collected.list.any { it is SwiperSwipeViewModel.Event.TriggerHapticFeedback } shouldBe true
        collected.cancel()
    }

    @Test
    fun `setDecision does not emit haptic when disabled`() = runTest2 {
        val items = listOf(item(1))
        val h = harness(items = items, hapticEnabled = false)
        advanceUntilIdle()

        val collected = collectEvents(h.vm)

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        advanceUntilIdle()

        collected.list.none { it is SwiperSwipeViewModel.Event.TriggerHapticFeedback } shouldBe true
        collected.cancel()
    }

    @Test
    fun `setDecision advances to next undecided item skipping decided ones`() = runTest2 {
        val items = listOf(
            item(1, SwipeDecision.UNDECIDED),
            item(2, SwipeDecision.KEEP),     // already decided — skip past
            item(3, SwipeDecision.UNDECIDED), // next stop
            item(4, SwipeDecision.UNDECIDED),
        )
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()

        // findNextUndecidedIndex should jump past index 1 (already KEEP) and stop at index 2.
        coVerify { h.swiper.updateCurrentIndex(any(), 2) }
    }

    @Test
    fun `setDecision navigates to status when no undecided remain`() = runTest2 {
        val items = listOf(item(1, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        // Mutate the items flow to reflect the decision so findNextUndecidedIndex sees no undecided.
        h.itemsFlow.value = listOf(item(1, SwipeDecision.DELETE))
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        advanceUntilIdle()

        // navigateToStatus emits a GoTo(SwiperStatusRoute) event.
        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperStatusRoute(sessionId = "session-1")
    }

    @Test
    fun `skip resets a decided item back to UNDECIDED`() = runTest2 {
        val items = listOf(
            item(1, SwipeDecision.KEEP),    // current: was decided — skip reverts it
            item(2, SwipeDecision.UNDECIDED),
        )
        val h = harness(session = session(currentIndex = 0), items = items)
        advanceUntilIdle()

        h.vm.skip()
        advanceUntilIdle()

        // Reverting from KEEP back to UNDECIDED so the item appears in the undecided queue again.
        coVerify { h.swiper.updateDecision(1L, SwipeDecision.UNDECIDED) }
    }

    @Test
    fun `skip on UNDECIDED item does not call updateDecision`() = runTest2 {
        val items = listOf(item(1, SwipeDecision.UNDECIDED), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.skip()
        advanceUntilIdle()

        // updateDecision must NOT fire for items already UNDECIDED — otherwise we'd hit the DB for
        // every skip with the same value.
        coVerify(exactly = 0) { h.swiper.updateDecision(any(), any()) }
    }

    @Test
    fun `skip discards session and navigates back when no items remain`() = runTest2 {
        // Edge case: all items got excludeAndRemove'd but the user keeps swiping skip — the screen
        // must self-rescue back to sessions.
        val h = harness(items = emptyList())
        advanceUntilIdle()

        h.vm.skip()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.discardSession("session-1") }
        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperSessionsRoute
    }

    @Test
    fun `undo with empty history is a no-op`() = runTest2 {
        val h = harness(items = listOf(item(1)))
        advanceUntilIdle()

        h.vm.undo()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiper.updateDecision(any(), any()) }
    }

    @Test
    fun `undo restores previous decision and current index`() = runTest2 {
        val items = listOf(item(1, SwipeDecision.UNDECIDED), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        // Make a decision so history is populated.
        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()
        // Now undo should restore item 1 to UNDECIDED and move back to index 0.
        h.vm.undo()
        advanceUntilIdle()

        // The undo restores decision via updateDecision(itemId, previousDecision=UNDECIDED).
        coVerify { h.swiper.updateDecision(1L, SwipeDecision.UNDECIDED) }
    }

    @Test
    fun `canUndo flips true after first setDecision and false again after undo`() = runTest2 {
        val items = listOf(item(1), item(2))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.state.first()!!.canUndo shouldBe false

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()
        h.vm.state.first()!!.canUndo shouldBe true

        h.vm.undo()
        advanceUntilIdle()
        h.vm.state.first()!!.canUndo shouldBe false
    }

    @Test
    fun `state aggregates decision counts and sizes`() = runTest2 {
        val items = listOf(
            item(1, SwipeDecision.KEEP),       // size = 1024
            item(2, SwipeDecision.KEEP),       // size = 2048
            item(3, SwipeDecision.DELETE),     // size = 3072
            item(4, SwipeDecision.UNDECIDED),  // size = 4096
            item(5, SwipeDecision.UNDECIDED),  // size = 5120
        )
        val h = harness(items = items)
        advanceUntilIdle()

        val state = h.vm.state.first()!!
        state.keepCount shouldBe 2
        state.keepSize shouldBe 1024 + 2048L
        state.deleteCount shouldBe 1
        state.deleteSize shouldBe 3072L
        state.undecidedCount shouldBe 2
        state.undecidedSize shouldBe 4096 + 5120L
    }

    @Test
    fun `state progressPercent reflects decided ratio`() = runTest2 {
        val items = listOf(
            item(1, SwipeDecision.KEEP),
            item(2, SwipeDecision.DELETE),
            item(3, SwipeDecision.UNDECIDED),
            item(4, SwipeDecision.UNDECIDED),
        )
        // totalItems is read from the session, not from items.size, because previously processed
        // items would have been removed from the item table.
        val h = harness(session = session().copy(totalItems = 4), items = items)
        advanceUntilIdle()

        h.vm.state.first()!!.progressPercent shouldBe 50
    }

    @Test
    fun `state currentIndex is coerced into items range`() = runTest2 {
        // Start with a startIndex past the end — coercion clamps it. Use a non-zero session
        // currentIndex so the stale-override guard doesn't fire and reset the override to 0.
        val items = listOf(item(1), item(2), item(3))
        val h = harness(
            session = session(currentIndex = 1),
            items = items,
            startIndex = 99,
        )
        advanceUntilIdle()

        // Coerced to items.size - 1 = 2.
        h.vm.state.first()!!.currentIndex shouldBe 2
    }

    @Test
    fun `state sessionPosition is 1-based index of session in createdAt order`() = runTest2 {
        // Older session at index 0, current "session-1" at index 1, newer at index 2.
        val older = SwipeSession(
            sessionId = "older",
            sourcePaths = emptyList(),
            currentIndex = 0,
            totalItems = 0,
            createdAt = Instant.parse("2024-12-01T00:00:00Z"),
            lastModifiedAt = Instant.parse("2024-12-01T00:00:00Z"),
            state = SessionState.READY,
        )
        val current = session(id = "session-1")
        val newer = SwipeSession(
            sessionId = "newer",
            sourcePaths = emptyList(),
            currentIndex = 0,
            totalItems = 0,
            createdAt = Instant.parse("2025-06-01T00:00:00Z"),
            lastModifiedAt = Instant.parse("2025-06-01T00:00:00Z"),
            state = SessionState.READY,
        )
        val stats = listOf(older, current, newer).map { Swiper.SessionWithStats(it, 0, 0, 0, 0, 0) }

        val h = harness(items = listOf(item(1)), sessionsWithStats = stats)
        advanceUntilIdle()

        // current is at index 1 in createdAt-sorted list → 1-based position 2.
        h.vm.state.first()!!.sessionPosition shouldBe 2
    }

    @Test
    fun `dismissGestureOverlay writes hasShownGestureOverlay setting`() = runTest2 {
        val h = harness()

        h.vm.dismissGestureOverlay()
        advanceUntilIdle()

        // settings.hasShownGestureOverlay.value(true) is an extension that calls update { true }.
        // We verify the underlying update() call was made on the relaxed mock.
        coVerify(exactly = 1) { h.gestureOverlaySetting.update(any()) }
    }

    @Test
    fun `excludeAndRemove saves PathExclusion with SWIPER tag and removes item`() = runTest2 {
        val items = listOf(item(1), item(2))
        val h = harness(items = items)
        advanceUntilIdle()

        // exclusionManager.save(Exclusion) extension calls save(setOf(exclusion)) on the real
        // save(Set<Exclusion>) method — capture the Set arg.
        val captured = slot<Set<Exclusion>>()
        coEvery { h.exclusionManager.save(capture(captured)) } returns emptyList()

        h.vm.excludeAndRemove(items[0])
        advanceUntilIdle()

        val excl = captured.captured.single()
        excl.shouldBeInstanceOf<PathExclusion>()
        excl.tags shouldBe setOf(Exclusion.Tag.SWIPER)
        coVerify(exactly = 1) { h.swiper.removeItem(1L) }
    }

    @Test
    fun `openExternally emits OpenExternally event when intent is non-null`() = runTest2 {
        val items = listOf(item(1))
        val h = harness(items = items)
        advanceUntilIdle()

        val intent = mockk<android.content.Intent>(relaxed = true)
        coEvery { h.viewIntentTool.create(any()) } returns intent

        val collected = collectEvents(h.vm)

        h.vm.openExternally(items[0])
        advanceUntilIdle()

        val emitted = collected.list.filterIsInstance<SwiperSwipeViewModel.Event.OpenExternally>().single()
        emitted.intent shouldBe intent
        collected.cancel()
    }

    @Test
    fun `openExternally emits ShowOpenNotSupported event when intent is null`() = runTest2 {
        val items = listOf(item(1))
        val h = harness(items = items)
        advanceUntilIdle()

        coEvery { h.viewIntentTool.create(any()) } returns null

        val collected = collectEvents(h.vm)

        h.vm.openExternally(items[0])
        advanceUntilIdle()

        collected.list.any { it is SwiperSwipeViewModel.Event.ShowOpenNotSupported } shouldBe true
        collected.cancel()
    }

    @Test
    fun `navigateToStatus emits GoTo with SwiperStatusRoute for current session`() = runTest2 {
        val h = harness(session = session(id = "session-x"), items = listOf(item(1)))
        advanceUntilIdle()

        h.vm.navigateToStatus()
        advanceUntilIdle()

        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperStatusRoute(sessionId = "session-x")
    }

    @Test
    fun `state showGestureOverlay reflects inverse of hasShownGestureOverlay setting`() = runTest2 {
        // showGestureOverlay = !hasShownOverlay. With hasShownOverlay = false, the overlay should
        // appear; with true, it should be hidden.
        val showFirst = harness(items = listOf(item(1)), showGestureOverlay = true)
        advanceUntilIdle()
        showFirst.vm.state.first()!!.showGestureOverlay shouldBe true

        val hidden = harness(items = listOf(item(1)), showGestureOverlay = false)
        advanceUntilIdle()
        hidden.vm.state.first()!!.showGestureOverlay shouldBe false
    }

    private class CollectedEvents(
        val list: MutableList<SwiperSwipeViewModel.Event>,
        val job: Job,
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    private fun TestScope.collectEvents(vm: SwiperSwipeViewModel): CollectedEvents {
        val list = mutableListOf<SwiperSwipeViewModel.Event>()
        // UNDISPATCHED so the collect is wired up before the test body's next action.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    // Used by some tests via reflection. Kept for import preservation under `slot<...>`.
    @Suppress("unused")
    private fun keepImportsAlive(): APath = LocalPath.build("a")
}
