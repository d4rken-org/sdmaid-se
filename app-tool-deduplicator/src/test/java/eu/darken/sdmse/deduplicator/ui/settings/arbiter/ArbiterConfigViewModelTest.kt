package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.ResultKey
import eu.darken.sdmse.common.picker.PickerRequest
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.picker.PickerRoute
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ArbiterConfigViewModelTest : BaseTest() {

    private class Harness(
        val vm: ArbiterConfigViewModel,
        val settings: DeduplicatorSettings,
        val arbiterConfigValue: DataStoreValue<DeduplicatorSettings.ArbiterConfig>,
        val arbiterConfigFlow: MutableStateFlow<DeduplicatorSettings.ArbiterConfig>,
        val pickerResults: MutableStateFlow<PickerResult?>,
        val capturedConsumeKey: io.mockk.CapturingSlot<ResultKey<PickerResult>>,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        private val job: Job,
    ) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectEvents(vm: ArbiterConfigViewModel): CollectedEvents<ArbiterConfigViewModel.Event> {
        val list = mutableListOf<ArbiterConfigViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(vm: ArbiterConfigViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        initial: DeduplicatorSettings.ArbiterConfig = DeduplicatorSettings.ArbiterConfig(),
    ): Harness {
        val flow = MutableStateFlow(initial)
        // NOTE: real `DataStoreValue.update` treats a transform returning `null` as
        // "reset-to-default". The current ArbiterConfigViewModel never returns null from its
        // transforms, so for this harness we collapse null to "no-op" (keeps the test simple).
        // Do NOT reuse this helper for code that exercises null-as-reset semantics.
        val arbiterConfigValue = mockk<DataStoreValue<DeduplicatorSettings.ArbiterConfig>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } answers {
                val transform = firstArg<(DeduplicatorSettings.ArbiterConfig) -> DeduplicatorSettings.ArbiterConfig?>()
                val newValue = transform(flow.value) ?: flow.value
                val old = flow.value
                flow.value = newValue
                DataStoreValue.Updated(old = old, new = newValue)
            }
        }
        val settings = mockk<DeduplicatorSettings>().apply {
            every { this@apply.arbiterConfig } returns arbiterConfigValue
        }
        val pickerResults = MutableStateFlow<PickerResult?>(null)
        val capturedKey = slot<ResultKey<PickerResult>>()
        val navCtrl = mockk<NavigationController>(relaxed = true).apply {
            every { consumeResults<PickerResult>(capture(capturedKey)) } returns flow {
                pickerResults.collect { value -> if (value != null) emit(value) }
            }
        }
        val vm = ArbiterConfigViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
            navCtrl = navCtrl,
        )
        return Harness(vm, settings, arbiterConfigValue, flow, pickerResults, capturedKey)
    }

    @Test
    fun `state criteria mirror the production defaults from DeduplicatorSettings`() = runTest2 {
        val h = harness()

        val state = h.vm.state.first()
        // Pin the canonical default ordering — a regression that reshuffles defaults flips this.
        state.criteria.map { it::class } shouldBe listOf(
            ArbiterCriterium.DuplicateType::class,
            ArbiterCriterium.PreferredPath::class,
            ArbiterCriterium.MediaProvider::class,
            ArbiterCriterium.Location::class,
            ArbiterCriterium.Nesting::class,
            ArbiterCriterium.Modified::class,
            ArbiterCriterium.Size::class,
        )
    }

    @Test
    fun `state criteria reflect non-default arbiter config`() = runTest2 {
        val custom = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_SMALLER),
                ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER),
            ),
        )
        val h = harness(initial = custom)

        val state = h.vm.state.first()
        state.criteria shouldBe custom.criteria
    }

    @Test
    fun `init subscribes to picker results with the production result key`() = runTest2 {
        // Regression: VM and PickerRoute share `PICKER_REQUEST_KEY`. If the consumer's key drifts
        // apart from the producer's request key, picker results land in a different channel and
        // the VM silently never wires up preferred paths.
        val h = harness()
        h.vm.state.first()

        h.capturedConsumeKey.captured.name shouldBe "picker.result.arbiter.keep.prefer.paths"
    }

    @Test
    fun `onCriteriumClick offers every Mode entry for each non-PreferredPath criterium`() = runTest2 {
        // Catches a regression that hardcodes a subset of modes for any criterium type.
        data class Case(val criterium: ArbiterCriterium, val expected: List<ArbiterCriterium.Mode>)
        val cases = listOf(
            Case(ArbiterCriterium.DuplicateType(), ArbiterCriterium.DuplicateType.Mode.entries),
            Case(ArbiterCriterium.MediaProvider(), ArbiterCriterium.MediaProvider.Mode.entries),
            Case(ArbiterCriterium.Location(), ArbiterCriterium.Location.Mode.entries),
            Case(ArbiterCriterium.Nesting(), ArbiterCriterium.Nesting.Mode.entries),
            Case(ArbiterCriterium.Modified(), ArbiterCriterium.Modified.Mode.entries),
            Case(ArbiterCriterium.Size(), ArbiterCriterium.Size.Mode.entries),
        )

        for ((criterium, expected) in cases) {
            val h = harness()

            h.vm.onCriteriumClick(criterium)

            val event = h.vm.events.first()
            event.shouldBeInstanceOf<ArbiterConfigViewModel.Event.ShowModeSelection>()
            event.criterium shouldBe criterium
            event.modes shouldBe expected
        }
    }

    @Test
    fun `onCriteriumClick with PreferredPath navigates to PickerRoute and emits no mode event`() = runTest2 {
        val h = harness()
        val events = collectEvents(h.vm)
        val nav = collectNavEvents(h.vm)

        h.vm.onCriteriumClick(ArbiterCriterium.PreferredPath(keepPreferPaths = setOf(LocalPath.build("storage", "kept"))))
        advanceUntilIdle()

        // No mode-selection event for PreferredPath.
        events.list shouldBe emptyList()

        val navEvent = nav.list.single()
        navEvent.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = navEvent.destination.shouldBeInstanceOf<PickerRoute>()
        route.request.requestKey shouldBe "arbiter.keep.prefer.paths"
        route.request.mode shouldBe PickerRequest.PickMode.DIRS
        route.request.selectedPaths shouldBe listOf(LocalPath.build("storage", "kept"))
        route.request.allowedAreas shouldBe setOf(
            DataArea.Type.PORTABLE,
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_DATA,
            DataArea.Type.PUBLIC_MEDIA,
        )

        events.cancel()
        nav.cancel()
    }

    @Test
    fun `onItemsReordered persists the new order verbatim and writes exactly once`() = runTest2 {
        val h = harness()
        val reordered = listOf(
            ArbiterCriterium.Size(),
            ArbiterCriterium.DuplicateType(),
            ArbiterCriterium.PreferredPath(),
        )

        h.vm.onItemsReordered(reordered)
        advanceUntilIdle()

        h.arbiterConfigFlow.value.criteria shouldBe reordered
        coVerify(exactly = 1) { h.arbiterConfigValue.update(any()) }
    }

    @Test
    fun `onModeSelected updates ONLY the matching criterium type and leaves others untouched`() = runTest2 {
        // Off-by-one risk: the VM picks the criterium by class match, not by index. This catches
        // a regression that selected by index and mutated the wrong criterium.
        val initial = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER),
                ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_OLDER),
                ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
            ),
        )
        val h = harness(initial = initial)

        h.vm.onModeSelected(
            ArbiterCriterium.Modified(),
            ArbiterCriterium.Modified.Mode.PREFER_NEWER,
        )
        advanceUntilIdle()

        val result = h.arbiterConfigFlow.value.criteria
        result shouldBe listOf(
            ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER),
            ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER),
            ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
        )
        coVerify(exactly = 1) { h.arbiterConfigValue.update(any()) }
    }

    @Test
    fun `onModeSelected on a criterium type absent from config leaves the list unchanged`() = runTest2 {
        // A regression that *appended* the changed criterium would grow the list; one that
        // mutated the wrong element would change the existing Size's mode. Use a non-default
        // Size mode and assert full list equality to catch both.
        val initial = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_SMALLER)),
        )
        val h = harness(initial = initial)

        h.vm.onModeSelected(
            ArbiterCriterium.Modified(),
            ArbiterCriterium.Modified.Mode.PREFER_NEWER,
        )
        advanceUntilIdle()

        h.arbiterConfigFlow.value shouldBe initial
    }

    @Test
    fun `resetToDefaults writes the canonical default ArbiterConfig`() = runTest2 {
        val custom = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_SMALLER)),
        )
        val h = harness(initial = custom)

        h.vm.resetToDefaults()
        advanceUntilIdle()

        h.arbiterConfigFlow.value shouldBe DeduplicatorSettings.ArbiterConfig()
        coVerify(exactly = 1) { h.arbiterConfigValue.update(any()) }
    }

    @Test
    fun `picker result REPLACES ONLY PreferredPath keepPreferPaths and leaves other criteria alone`() = runTest2 {
        val initial = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.DuplicateType(),
                ArbiterCriterium.PreferredPath(keepPreferPaths = setOf(LocalPath.build("storage", "old"))),
                ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER),
            ),
        )
        val h = harness(initial = initial)
        h.vm.state.first() // prime init flow

        val newPath = LocalPath.build("storage", "new")
        h.pickerResults.value = PickerResult(selectedPaths = setOf(newPath))
        advanceUntilIdle()

        val result = h.arbiterConfigFlow.value.criteria
        // Full list comparison: order preserved, non-PreferredPath items untouched, PreferredPath
        // paths REPLACED (not merged) by the picker selection.
        result shouldBe listOf(
            ArbiterCriterium.DuplicateType(),
            ArbiterCriterium.PreferredPath(keepPreferPaths = setOf(newPath)),
            ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER),
        )
    }

    @Test
    fun `state stays in sync when arbiterConfig flow emits new values`() = runTest2 {
        val h = harness()
        h.vm.state.first()

        val updated = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(ArbiterCriterium.Size()),
        )
        h.arbiterConfigFlow.value = updated
        advanceUntilIdle()

        h.vm.state.first().criteria shouldBe updated.criteria
    }
}
