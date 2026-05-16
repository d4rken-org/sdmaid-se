package eu.darken.sdmse.systemcleaner.ui.customfilter.list

import android.content.Context
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.filter.CustomFilterEditorRoute
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterConfig
import eu.darken.sdmse.systemcleaner.core.filter.custom.CustomFilterRepo
import eu.darken.sdmse.systemcleaner.core.rwDataStoreValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class CustomFilterListViewModelTest : BaseTest() {

    private fun config(id: String, label: String): CustomFilterConfig = CustomFilterConfig(
        identifier = id,
        label = label,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class Harness(
        val vm: CustomFilterListViewModel,
        val repo: CustomFilterRepo,
        val settings: SystemCleanerSettings,
        val enabledFilterValue: eu.darken.sdmse.common.datastore.DataStoreValue<Set<String>>,
        val upgradeRepo: UpgradeRepo,
        val webpageTool: WebpageTool,
        val configsFlow: MutableStateFlow<Collection<CustomFilterConfig>>,
        val upgradeFlow: MutableSharedFlow<UpgradeRepo.Info>,
    )

    private fun buildHarness(
        configs: Collection<CustomFilterConfig> = emptyList(),
        enabled: Set<String> = emptySet(),
        emitUpgradeInfo: Boolean = true,
        isPro: Boolean = false,
    ): Harness {
        val configsFlow = MutableStateFlow(configs)
        val upgradeFlow = MutableSharedFlow<UpgradeRepo.Info>(replay = if (emitUpgradeInfo) 1 else 0)
        if (emitUpgradeInfo) {
            upgradeFlow.tryEmit(mockk<UpgradeRepo.Info>().apply { every { this@apply.isPro } returns isPro })
        }
        val repo = mockk<CustomFilterRepo>(relaxed = true).apply {
            every { this@apply.configs } returns configsFlow
        }
        val enabledFilterValue = rwDataStoreValue(enabled)
        val settings = mockk<SystemCleanerSettings>().apply {
            every { enabledCustomFilter } returns enabledFilterValue
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns upgradeFlow
        }
        val webpageTool = mockk<WebpageTool>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val vm = CustomFilterListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            context = context,
            customFilterRepo = repo,
            systemCleanerSettings = settings,
            upgradeRepo = upgradeRepo,
            webpageTool = webpageTool,
        )
        return Harness(vm, repo, settings, enabledFilterValue, upgradeRepo, webpageTool, configsFlow, upgradeFlow)
    }

    @Test
    fun `state rows are sorted alphabetically by label`() = runTest2 {
        val h = buildHarness(
            configs = listOf(
                config("z", "Zebra filter"),
                config("a", "Alpha filter"),
                config("m", "Middle filter"),
            ),
        )
        h.vm.state.first().rows.map { it.config.identifier } shouldBe listOf("a", "m", "z")
    }

    @Test
    fun `state isPro is null while upgradeInfo has not emitted yet`() = runTest2 {
        // Before upgradeInfo emits, the `onStart { emit(null) }` shim emits null.
        val h = buildHarness(emitUpgradeInfo = false)
        h.vm.state.first().isPro shouldBe null
    }

    @Test
    fun `state isPro becomes true after upgradeInfo emits pro user`() = runTest2 {
        val h = buildHarness(isPro = true)
        h.vm.state.first().isPro shouldBe true
    }

    @Test
    fun `state rows isEnabled reflects enabledCustomFilter setting`() = runTest2 {
        val a = config("a", "A")
        val b = config("b", "B")
        val h = buildHarness(configs = listOf(a, b), enabled = setOf("a"))

        val rows = h.vm.state.first().rows
        rows.single { it.config.identifier == "a" }.isEnabled shouldBe true
        rows.single { it.config.identifier == "b" }.isEnabled shouldBe false
    }

    @Test
    fun `onToggleRow calls toggleCustomFilter via settings`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness(configs = listOf(a))

        h.vm.onToggleRow(CustomFilterListViewModel.FilterRow(config = a, isEnabled = false))
        advanceUntilIdle()

        // toggleCustomFilter is an extension that calls update on enabledCustomFilter.
        coVerify(atLeast = 1) { h.enabledFilterValue.update(any()) }
    }

    @Test
    fun `onEditClick when not pro navigates to UpgradeRoute`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness(configs = listOf(a), isPro = false)

        h.vm.onEditClick(CustomFilterListViewModel.FilterRow(config = a, isEnabled = false))
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination.shouldBeInstanceOf<UpgradeRoute>()
    }

    @Test
    fun `onEditClick when pro navigates to CustomFilterEditorRoute`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness(configs = listOf(a), isPro = true)

        h.vm.onEditClick(CustomFilterListViewModel.FilterRow(config = a, isEnabled = false))
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val dest = event.destination
        dest.shouldBeInstanceOf<CustomFilterEditorRoute>()
        dest.identifier shouldBe "a"
    }

    @Test
    fun `onCreateClick when not pro navigates to UpgradeRoute`() = runTest2 {
        val h = buildHarness(isPro = false)

        h.vm.onCreateClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination.shouldBeInstanceOf<UpgradeRoute>()
    }

    @Test
    fun `onCreateClick when pro navigates to CustomFilterEditorRoute with initial options and null identifier`() = runTest2 {
        val h = buildHarness(isPro = true)

        h.vm.onCreateClick()
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val dest = event.destination
        dest.shouldBeInstanceOf<CustomFilterEditorRoute>()
        dest.identifier shouldBe null
        (dest.initial != null) shouldBe true
    }

    @Test
    fun `removeRows calls repo remove with identifiers and emits UndoRemove event`() = runTest2 {
        val a = config("a", "A")
        val b = config("b", "B")
        val h = buildHarness(configs = listOf(a, b))
        val rows = listOf(
            CustomFilterListViewModel.FilterRow(config = a, isEnabled = false),
            CustomFilterListViewModel.FilterRow(config = b, isEnabled = false),
        )

        h.vm.removeRows(rows)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.remove(setOf("a", "b")) }
        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CustomFilterListViewModel.Event.UndoRemove>()
        event.configs shouldBe setOf(a, b)
    }

    @Test
    fun `restore calls repo save with the given configs`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness()

        h.vm.restore(setOf(a))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.repo.save(setOf(a)) }
    }

    @Test
    fun `exportRows when not pro navigates to UpgradeRoute`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness(configs = listOf(a), isPro = false)

        h.vm.exportRows(listOf(CustomFilterListViewModel.FilterRow(config = a, isEnabled = false)))
        advanceUntilIdle()

        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination.shouldBeInstanceOf<UpgradeRoute>()
    }

    @Test
    fun `exportRows when pro emits LaunchExport event after staging`() = runTest2 {
        val a = config("a", "A")
        val h = buildHarness(configs = listOf(a), isPro = true)
        coEvery { h.repo.exportFilters(any()) } returns emptyList()

        h.vm.exportRows(listOf(CustomFilterListViewModel.FilterRow(config = a, isEnabled = false)))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CustomFilterListViewModel.Event.LaunchExport>()
        // exportFilters must have been called before LaunchExport was emitted (otherwise the
        // staged payload wouldn't be ready for performExport).
        coVerify(exactly = 1) { h.repo.exportFilters(listOf("a")) }
    }

    @Test
    fun `onHelpClick opens help URL via webpage tool`() = runTest2 {
        val h = buildHarness()
        h.vm.onHelpClick()
        advanceUntilIdle()
        io.mockk.verify(exactly = 1) { h.webpageTool.open(any<String>()) }
    }
}
