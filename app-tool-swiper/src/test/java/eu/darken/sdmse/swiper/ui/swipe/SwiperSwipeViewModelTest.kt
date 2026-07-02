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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.io.IOException
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
        val activeSessionsFlow: MutableStateFlow<List<SwipeSession>>,
        val hapticSetting: DataStoreValue<Boolean>,
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
        sessions: List<SwipeSession> = listOfNotNull(session),
    ): Harness {
        val sessionFlow = MutableStateFlow(session)
        val itemsFlow = MutableStateFlow(items)
        val activeSessionsFlow = MutableStateFlow(sessions)

        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSession(any()) } returns sessionFlow
            every { getItemsForSession(any()) } returns itemsFlow
            every { activeSessions } returns activeSessionsFlow
            coEvery { hasSessionLookups(any()) } returns hasSessionLookups
        }

        val hapticSetting = mockSetting(hapticEnabled)
        val settings = mockk<SwiperSettings>().apply {
            every { swapSwipeDirections } returns mockSetting(false)
            every { showFileDetailsOverlay } returns mockSetting(true)
            every { hapticFeedbackEnabled } returns hapticSetting
        }
        val exclusionManager = mockk<ExclusionManager>(relaxed = true)
        val viewIntentTool = mockk<ViewIntentTool>(relaxed = true)

        val vm = SwiperSwipeViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            appScope = backgroundScope,
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
            activeSessionsFlow = activeSessionsFlow,
            hapticSetting = hapticSetting,
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
    fun `bindRoute with startIndex applies it as override regardless of session current index`() = runTest2 {
        val items = (1..5).map { item(it.toLong()) }

        val fresh = harness(session = session(currentIndex = 0), items = items, startIndex = 3)
        advanceUntilIdle()
        fresh.vm.state.first()!!.currentIndex shouldBe 3

        val midSession = harness(session = session(currentIndex = 1), items = items, startIndex = 3)
        advanceUntilIdle()
        midSession.vm.state.first()!!.currentIndex shouldBe 3
    }

    @Test
    fun `state currentIndex coerces an out-of-range override to the last item`() = runTest2 {
        val items = (1..3).map { item(it.toLong()) }
        val h = harness(session = session(currentIndex = 1), items = items, startIndex = 99)
        advanceUntilIdle()

        h.vm.state.first()!!.currentIndex shouldBe 2
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
        h.vm.state.first()!!.currentIndex shouldBe 1
    }

    @Test
    fun `setDecision advances optimistically before the DB write happens`() = runTest2 {
        // The visible cursor must move synchronously (no advanceUntilIdle) so the next card promotes
        // within a frame; the DB write is enqueued and runs later.
        val items = listOf(item(1), item(2), item(3))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        // No advanceUntilIdle: the cursor already moved.
        h.vm.state.first()!!.currentIndex shouldBe 1
        // The decided item already reads as DELETE via the optimistic overlay.
        h.vm.state.first()!!.deleteCount shouldBe 1
    }

    @Test
    fun `setDecision ignores a stale commit for a non-current item`() = runTest2 {
        val items = listOf(item(1), item(2))
        val h = harness(items = items)
        advanceUntilIdle()

        // Current item is 1; a late callback for item 2 must be ignored.
        h.vm.setDecision(itemId = 2L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiper.updateDecision(2L, any()) }
        h.vm.state.first()!!.currentIndex shouldBe 0
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

        coVerify { h.swiper.updateCurrentIndex(any(), 2) }
    }

    @Test
    fun `setDecision navigates to status when no undecided remain`() = runTest2 {
        // Deciding the only item leaves nothing undecided — the optimistic overlay means we don't
        // need to wait for Room to reflect the write before navigating.
        val items = listOf(item(1, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.DELETE)
        advanceUntilIdle()

        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperStatusRoute(sessionId = "session-1")
    }

    @Test
    fun `rapid decisions on the last cards do not wrap back to a just-decided card`() = runTest2 {
        // Two undecided items; decide both back-to-back WITHOUT letting Room re-emit between them.
        // Without the optimistic overlay, the second advance would wrap around and revisit item 1
        // (still UNDECIDED in the stale items list) instead of navigating to status.
        val items = listOf(item(1, SwipeDecision.UNDECIDED), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        h.vm.setDecision(itemId = 2L, decision = SwipeDecision.DELETE)
        advanceUntilIdle()

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

        h.vm.skip(1L)
        advanceUntilIdle()

        coVerify { h.swiper.updateDecision(1L, SwipeDecision.UNDECIDED) }
    }

    @Test
    fun `skip on UNDECIDED item does not call updateDecision`() = runTest2 {
        val items = listOf(item(1, SwipeDecision.UNDECIDED), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.skip(1L)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiper.updateDecision(any(), any()) }
    }

    @Test
    fun `skip on a decided-only deck resets the item and navigates to status`() = runTest2 {
        // Documented edge case: skipping the only (decided) card resets it to UNDECIDED, but there is
        // no other undecided card to advance to, so we land on the status screen.
        val items = listOf(item(1, SwipeDecision.KEEP))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.skip(1L)
        advanceUntilIdle()

        coVerify { h.swiper.updateDecision(1L, SwipeDecision.UNDECIDED) }
        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperStatusRoute(sessionId = "session-1")
    }

    @Test
    fun `skip discards session and navigates back when no items remain`() = runTest2 {
        val h = harness(items = emptyList())
        advanceUntilIdle()

        h.vm.skip(1L)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.discardSession("session-1") }
        val nav = h.vm.navEvents.first()
        nav.shouldBeInstanceOf<NavEvent.GoTo>()
        nav.destination shouldBe SwiperSessionsRoute
    }

    @Test
    fun `skip ignores a stale commit for a non-current item`() = runTest2 {
        // Current item is 1 (decided KEEP); a late skip for item 2 must be ignored, so item 1's
        // decision is NOT reset.
        val items = listOf(item(1, SwipeDecision.KEEP), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.skip(2L)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiper.updateDecision(any(), any()) }
        h.vm.state.first()!!.currentIndex shouldBe 0
    }

    @Test
    fun `keep then undo then keep on the same item ends optimistically KEEP`() = runTest2 {
        // Exercises the decide→undo→decide burst on one item: the final optimistic state must be KEEP
        // and must not be clobbered by the intermediate undo's reconciliation.
        val items = listOf(item(1, SwipeDecision.UNDECIDED), item(2, SwipeDecision.UNDECIDED))
        val h = harness(items = items)
        advanceUntilIdle()

        h.vm.setDecision(1L, SwipeDecision.KEEP)
        h.vm.undo()
        h.vm.setDecision(1L, SwipeDecision.KEEP)
        advanceUntilIdle()

        val state = h.vm.state.first()!!
        state.items.first { it.id == 1L }.decision shouldBe SwipeDecision.KEEP
        state.keepCount shouldBe 1
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

        h.vm.setDecision(itemId = 1L, decision = SwipeDecision.KEEP)
        advanceUntilIdle()
        h.vm.undo()
        advanceUntilIdle()

        coVerify { h.swiper.updateDecision(1L, SwipeDecision.UNDECIDED) }
        coVerify { h.swiper.updateCurrentIndex(any(), 0) }
        h.vm.state.first()!!.currentIndex shouldBe 0
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
        val h = harness(session = session().copy(totalItems = 4), items = items)
        advanceUntilIdle()

        h.vm.state.first()!!.progressPercent shouldBe 50
    }

    @Test
    fun `state sessionPosition is 1-based index of session in createdAt order`() = runTest2 {
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

        val h = harness(items = listOf(item(1)), sessions = listOf(older, current, newer))
        advanceUntilIdle()

        // current is at index 1 in createdAt-sorted list → 1-based position 2.
        h.vm.state.first()!!.sessionPosition shouldBe 2
    }

    @Test
    fun `excludeAndRemove saves PathExclusion with SWIPER tag and removes item`() = runTest2 {
        val items = listOf(item(1), item(2))
        val h = harness(items = items)
        advanceUntilIdle()

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
    fun `nextUndecidedIndex skips decided and pending-decided items and wraps around`() {
        val items = listOf(item(1), item(2), item(3), item(4))

        // From 0, next undecided is 1.
        nextUndecidedIndex(items, emptyMap(), fromIndex = 0, excludeItemId = null) shouldBe 1

        // Pending DELETE on item 2 (index 1) is skipped → 2.
        nextUndecidedIndex(items, mapOf(2L to SwipeDecision.DELETE), fromIndex = 0, excludeItemId = null) shouldBe 2

        // Excluded current item is skipped even though undecided.
        nextUndecidedIndex(items, emptyMap(), fromIndex = 0, excludeItemId = 2L) shouldBe 2

        // Wrap-around: from the last index, find an earlier undecided item.
        nextUndecidedIndex(items, emptyMap(), fromIndex = 3, excludeItemId = null) shouldBe 0

        // Everything decided (persisted or pending) → null.
        val allDecided = mapOf(
            1L to SwipeDecision.KEEP,
            2L to SwipeDecision.DELETE,
            3L to SwipeDecision.KEEP,
            4L to SwipeDecision.DELETE,
        )
        nextUndecidedIndex(items, allDecided, fromIndex = 0, excludeItemId = null) shouldBe null
    }

    // ─────────────────────────── persist-failure containment & deck integrity ───────────────────────────

    @Test
    fun `a failed decision write rolls back the optimistic overlay and surfaces the error`() = runTest2 {
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()
        coEvery { h.swiper.updateDecision(any(), any()) } throws IOException("db write failed")
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.errorEvents.collect { errors.add(it) } }

        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        advanceUntilIdle()

        // Optimistic DELETE rolled back to the DB truth (UNDECIDED)...
        h.vm.state.value!!.items.first { it.id == 1L }.decision shouldBe SwipeDecision.UNDECIDED
        // ...its undo entry dropped...
        h.vm.state.value!!.canUndo shouldBe false
        // ...and the failure surfaced instead of being swallowed.
        errors shouldHaveSize 1
        errorJob.cancel()
    }

    @Test
    fun `a failed undo write restores the overlay and the undo entry`() = runTest2 {
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()

        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        advanceUntilIdle()
        // Simulate the DB echoing the successful first write.
        h.itemsFlow.value = listOf(item(1, SwipeDecision.DELETE), item(2))
        advanceUntilIdle()

        coEvery { h.swiper.updateDecision(any(), any()) } throws IOException("db write failed")
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.errorEvents.collect { errors.add(it) } }

        h.vm.undo()
        advanceUntilIdle()

        // The deck falls back to the recorded DELETE instead of claiming a rescued item that
        // finalizing from the status screen would still delete...
        h.vm.state.value!!.items.first { it.id == 1L }.decision shouldBe SwipeDecision.DELETE
        // ...the undo entry is restored so it can be retried...
        h.vm.state.value!!.canUndo shouldBe true
        // ...and the failure surfaced.
        errors shouldHaveSize 1
        errorJob.cancel()
    }

    @Test
    fun `a failed index write after a successful decision write does not roll back`() = runTest2 {
        // The decision itself persisted; only the cosmetic cursor write failed. Rolling back the
        // overlay/undo entry here would hide a decision that IS recorded in the DB.
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()
        coEvery { h.swiper.updateCurrentIndex(any(), any()) } throws IOException("index write failed")
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.errorEvents.collect { errors.add(it) } }

        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        advanceUntilIdle()

        h.vm.state.value!!.items.first { it.id == 1L }.decision shouldBe SwipeDecision.DELETE
        h.vm.state.value!!.canUndo shouldBe true
        errors shouldHaveSize 0
        errorJob.cancel()
    }

    @Test
    fun `a superseded persist failure does not clobber the newer decision`() = runTest2 {
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()
        var calls = 0
        coEvery { h.swiper.updateDecision(any(), any()) } coAnswers {
            delay(100)
            if (++calls == 1) throw IOException("first write fails late")
        }
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.errorEvents.collect { errors.add(it) } }

        // decide -> undo -> decide again while the first (failing) write is still in the queue.
        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        h.vm.undo()
        h.vm.setDecision(1L, SwipeDecision.KEEP) shouldBe true
        advanceUntilIdle()

        // The old failure must neither clobber the newer overlay nor drop its undo entry...
        h.vm.state.value!!.items.first { it.id == 1L }.decision shouldBe SwipeDecision.KEEP
        h.vm.state.value!!.canUndo shouldBe true
        // ...and the superseded failure isn't surfaced - the newer write governs the final state.
        errors shouldHaveSize 0
        errorJob.cancel()
    }

    @Test
    fun `a superseded persist failure does not clobber a same-value newer decision`() = runTest2 {
        // DELETE -> undo -> DELETE: comparing overlay values can't tell the old failed write from
        // the newer accepted one — only the write generation can.
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()
        var calls = 0
        coEvery { h.swiper.updateDecision(any(), any()) } coAnswers {
            delay(100)
            if (++calls == 1) throw IOException("first write fails late")
        }
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.errorEvents.collect { errors.add(it) } }

        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        h.vm.undo()
        h.vm.setDecision(1L, SwipeDecision.DELETE) shouldBe true
        advanceUntilIdle()

        h.vm.state.value!!.items.first { it.id == 1L }.decision shouldBe SwipeDecision.DELETE
        h.vm.state.value!!.canUndo shouldBe true
        errors shouldHaveSize 0
        errorJob.cancel()
    }

    @Test
    fun `stale commits report rejection so no fly-off is played for them`() = runTest2 {
        val h = harness(items = listOf(item(1), item(2)))
        advanceUntilIdle()

        // Item 2 is not the current card; nothing is recorded, and the screen must not stamp a
        // KEEP/DELETE fly-off for it.
        h.vm.setDecision(2L, SwipeDecision.DELETE) shouldBe false
        h.vm.skip(2L) shouldBe false
    }

    @Test
    fun `back-card preview mirrors the actual deck advance`() = runTest2 {
        // The raw list successor is already decided; the deck's advance skips it, so the peeking
        // back-card must too — otherwise the promoted card mounts fresh and flashes blank.
        val h = harness(items = listOf(item(1), item(2, SwipeDecision.KEEP), item(3)))
        advanceUntilIdle()

        h.vm.state.value!!.nextItem?.id shouldBe 3L
    }

    @Test
    fun `excluding the current card advances to its direct successor`() = runTest2 {
        val h = harness(items = listOf(item(1), item(2), item(3)))
        advanceUntilIdle()
        coEvery { h.swiper.removeItem(any()) } answers {
            val removed = arg<Long>(0)
            h.itemsFlow.value = h.itemsFlow.value.filterNot { it.id == removed }
        }

        h.vm.excludeAndRemove(h.vm.state.value!!.items.first())
        advanceUntilIdle()

        // After removing item 1, item 2 sits at the cursor and is the next card — the old search
        // started past it and additionally excluded it, bypassing it until wrap-around.
        h.vm.state.value!!.currentItem?.id shouldBe 2L
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
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    // Used by some tests via reflection. Kept for import preservation under `slot<...>`.
    @Suppress("unused")
    private fun keepImportsAlive(): APath = LocalPath.build("a")
}
