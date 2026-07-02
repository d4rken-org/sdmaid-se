package eu.darken.sdmse.appcontrol.ui.list

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlScanTask
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.appcontrol.core.archive.ArchiveTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.core.usage.UsageInfo
import eu.darken.sdmse.appcontrol.ui.AppActionRoute
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class AppControlListViewModelTest : BaseTest() {

    // ─────────────────────────── helpers ───────────────────────────

    private val systemUserHandle = UserHandle2(handleId = 0)

    private fun appInfo(
        pkgName: String,
        label: String = pkgName,
        userHandle: UserHandle2 = systemUserHandle,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
        isActive: Boolean? = null,
        sizes: PkgOps.SizeStats? = null,
        usage: UsageInfo? = null,
        updatedAt: Instant? = null,
        installedAt: Instant? = null,
    ): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        val installId = InstallId(pkgId, userHandle)
        val labelCa: CaString = label.toCaString()

        val pkg = mockk<Installed>(
            relaxed = true,
            moreInterfaces = arrayOf(InstallDetails::class),
        ).apply {
            every { id } returns pkgId
            every { this@apply.installId } returns installId
            every { this@apply.userHandle } returns userHandle
            every { this@apply.label } returns labelCa
            every { packageName } returns pkgName
            // InstallDetails fields driving the filter/sort logic
            every { (this@apply as InstallDetails).isEnabled } returns isEnabled
            every { (this@apply as InstallDetails).isSystemApp } returns isSystem
            every { (this@apply as InstallDetails).updatedAt } returns updatedAt
            every { (this@apply as InstallDetails).installedAt } returns installedAt
        }

        return AppInfo(
            pkg = pkg,
            isActive = isActive,
            sizes = sizes,
            usage = usage,
            userProfile = null,
            canBeToggled = false,
            canBeStopped = false,
            canBeExported = false,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = false,
        )
    }

    private fun appControlState(
        data: AppControl.Data? = null,
        progress: Progress.Data? = null,
        canToggle: Boolean = true,
        canForceStop: Boolean = true,
        canArchive: Boolean = false,
        canRestore: Boolean = false,
        canInfoActive: Boolean = true,
        canInfoSize: Boolean = true,
        canInfoScreenTime: Boolean = true,
        canIncludeMultiUser: Boolean = false,
        missingSetup: Set<SetupModule.Type> = emptySet(),
    ): AppControl.State = AppControl.State(
        data = data,
        progress = progress,
        canToggle = canToggle,
        canForceStop = canForceStop,
        canArchive = canArchive,
        canRestore = canRestore,
        canInfoActive = canInfoActive,
        canInfoSize = canInfoSize,
        canInfoScreenTime = canInfoScreenTime,
        canIncludeMultiUser = canIncludeMultiUser,
        missingSetup = missingSetup,
    )

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    /**
     * Mutable fake: `update {}` applies the transformer to a backing MutableStateFlow, so
     * `value()` reads after writes (e.g. buildScanTask reading listSort post-persist) see the
     * new value instead of the static initial.
     */
    private fun <T> mutableDataStoreValue(initial: T): Pair<DataStoreValue<T>, MutableStateFlow<T>> {
        val backing = MutableStateFlow(initial)
        val value = mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns backing
            coEvery { update(any()) } answers {
                val old = backing.value
                @Suppress("UNCHECKED_CAST")
                val new = (firstArg() as (T) -> T).invoke(old)
                backing.value = new
                DataStoreValue.Updated(old = old, new = new)
            }
        }
        return value to backing
    }

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { type } returns UpgradeRepo.Type.FOSS
        every { upgradedAt } returns null
        every { error } returns null
    }

    private class Harness(
        val vm: AppControlListViewModel,
        val appControl: AppControl,
        val taskSubmitter: TaskSubmitter,
        val exclusionManager: ExclusionManager,
        val upgradeRepo: UpgradeRepo,
        val settings: AppControlSettings,
        val usageStatsSetupModule: SetupModule,
        val storageSetupModule: SetupModule,
        val listSort: DataStoreValue<SortSettings>,
        val listSortBacking: MutableStateFlow<SortSettings>,
        val listFilter: DataStoreValue<FilterSettings>,
        val ackSizeSortCaveat: DataStoreValue<Boolean>,
        val listFastScrollerEnabled: DataStoreValue<Boolean>,
        val moduleSizingEnabled: DataStoreValue<Boolean>,
        val moduleActivityEnabled: DataStoreValue<Boolean>,
        val includeMultiUserEnabled: DataStoreValue<Boolean>,
        val stateFlow: MutableStateFlow<AppControl.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        val job: Job,
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    private fun CoroutineScope.collectEvents(
        vm: AppControlListViewModel,
    ): CollectedEvents<AppControlListViewModel.Event> {
        val list = mutableListOf<AppControlListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(
        vm: AppControlListViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        data: AppControl.Data? = AppControl.Data(
            apps = emptyList(),
            hasInfoScreenTime = false,
            hasInfoActive = false,
            hasInfoSize = false,
            hasIncludedMultiUser = false,
        ),
        progress: Progress.Data? = null,
        sort: SortSettings = SortSettings(),
        filter: FilterSettings = FilterSettings(),
        sizingEnabled: Boolean = true,
        activityEnabled: Boolean = true,
        multiUserEnabled: Boolean = false,
        fastScrollerEnabled: Boolean = false,
        ackSizeSortCaveatValue: Boolean = false,
        isPro: Boolean = false,
        canToggle: Boolean = true,
        canForceStop: Boolean = true,
        canArchive: Boolean = false,
        canRestore: Boolean = false,
        canInfoActive: Boolean = true,
        canInfoSize: Boolean = true,
        canInfoScreenTime: Boolean = true,
        missingSetup: Set<SetupModule.Type> = emptySet(),
    ): Harness {
        val stateFlow = MutableStateFlow(
            appControlState(
                data = data,
                progress = progress,
                canToggle = canToggle,
                canForceStop = canForceStop,
                canArchive = canArchive,
                canRestore = canRestore,
                canInfoActive = canInfoActive,
                canInfoSize = canInfoSize,
                canInfoScreenTime = canInfoScreenTime,
                missingSetup = missingSetup,
            ),
        )
        val progressFlow = MutableStateFlow(progress)
        val appControl = mockk<AppControl>(relaxed = true).apply {
            every { state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val (listSort, listSortBacking) = mutableDataStoreValue(sort)
        val listFilter = rwDataStoreValue(filter)
        val ackSizeSortCaveatStore = rwDataStoreValue(ackSizeSortCaveatValue)
        val listFastScrollerEnabledStore = rwDataStoreValue(fastScrollerEnabled)
        val moduleSizingEnabledStore = rwDataStoreValue(sizingEnabled)
        val moduleActivityEnabledStore = rwDataStoreValue(activityEnabled)
        val includeMultiUserEnabledStore = rwDataStoreValue(multiUserEnabled)
        val settings = mockk<AppControlSettings>().apply {
            every { this@apply.listSort } returns listSort
            every { this@apply.listFilter } returns listFilter
            every { ackSizeSortCaveat } returns ackSizeSortCaveatStore
            every { listFastScrollerEnabled } returns listFastScrollerEnabledStore
            every { moduleSizingEnabled } returns moduleSizingEnabledStore
            every { moduleActivityEnabled } returns moduleActivityEnabledStore
            every { includeMultiUserEnabled } returns includeMultiUserEnabledStore
        }
        val exclusionManager = mockk<ExclusionManager>(relaxed = true)
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { this@apply.upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
            every { this@apply.isSettled } returns flowOf(true)
        }
        val context = mockk<Context>(relaxed = true)
        val usageStatsSetupModule = mockk<SetupModule>(relaxed = true)
        val storageSetupModule = mockk<SetupModule>(relaxed = true)

        val vm = AppControlListViewModel(
            handle = SavedStateHandle(),
            dispatcherProvider = TestDispatcherProvider(),
            context = context,
            appControl = appControl,
            settings = settings,
            exclusionManager = exclusionManager,
            upgradeRepo = upgradeRepo,
            taskManager = taskSubmitter,
            usageStatsSetupModule = usageStatsSetupModule,
            storageSetupModule = storageSetupModule,
        )
        return Harness(
            vm = vm,
            appControl = appControl,
            taskSubmitter = taskSubmitter,
            exclusionManager = exclusionManager,
            upgradeRepo = upgradeRepo,
            settings = settings,
            usageStatsSetupModule = usageStatsSetupModule,
            storageSetupModule = storageSetupModule,
            listSort = listSort,
            listSortBacking = listSortBacking,
            listFilter = listFilter,
            ackSizeSortCaveat = ackSizeSortCaveatStore,
            listFastScrollerEnabled = listFastScrollerEnabledStore,
            moduleSizingEnabled = moduleSizingEnabledStore,
            moduleActivityEnabled = moduleActivityEnabledStore,
            includeMultiUserEnabled = includeMultiUserEnabledStore,
            stateFlow = stateFlow,
            progressFlow = progressFlow,
        )
    }

    private fun dataOf(vararg apps: AppInfo, hasInfoSize: Boolean = false): AppControl.Data =
        AppControl.Data(
            apps = apps.toList(),
            hasInfoScreenTime = false,
            hasInfoActive = false,
            hasInfoSize = hasInfoSize,
            hasIncludedMultiUser = false,
        )

    // ─────────────────────────── init / scan ───────────────────────────

    @Test
    fun `init submits a scan task when AppControl has no data`() = runTest2 {
        val h = harness(data = null)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submit(any<AppControlScanTask>()) }
    }

    @Test
    fun `init does NOT submit a scan task when AppControl already has data`() = runTest2 {
        // Regression guard: re-entering the screen with existing data must not double-scan.
        val h = harness(data = dataOf())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `state rows is null when Data is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first().rows shouldBe null
    }

    @Test
    fun `state rows is empty when apps list is empty`() = runTest2 {
        val h = harness(data = dataOf())
        h.vm.state.first().rows!! shouldBe emptyList()
    }

    // ─────────────────────────── filtering ───────────────────────────

    @Test
    fun `filter USER excludes system apps`() = runTest2 {
        val user = appInfo("com.user.app", isSystem = false)
        val system = appInfo("com.android.system", isSystem = true)
        val h = harness(
            data = dataOf(user, system),
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.USER)),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.user.app")
    }

    @Test
    fun `filter SYSTEM excludes user apps`() = runTest2 {
        val user = appInfo("com.user.app", isSystem = false)
        val system = appInfo("com.android.system", isSystem = true)
        val h = harness(
            data = dataOf(user, system),
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.SYSTEM)),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.android.system")
    }

    @Test
    fun `filter ENABLED excludes disabled apps`() = runTest2 {
        val enabled = appInfo("com.enabled.app", isEnabled = true)
        val disabled = appInfo("com.disabled.app", isEnabled = false)
        val h = harness(
            data = dataOf(enabled, disabled),
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.ENABLED)),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.enabled.app")
    }

    @Test
    fun `filter DISABLED excludes enabled apps`() = runTest2 {
        val enabled = appInfo("com.enabled.app", isEnabled = true)
        val disabled = appInfo("com.disabled.app", isEnabled = false)
        val h = harness(
            data = dataOf(enabled, disabled),
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.DISABLED)),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.disabled.app")
    }

    @Test
    fun `filter ACTIVE excludes apps explicitly known to be inactive`() = runTest2 {
        // The filter only drops apps with isActive == false. Apps with null (unknown activity)
        // pass through — the logic is `isActive == false` not `isActive != true`.
        val active = appInfo("com.active.app", isActive = true)
        val inactive = appInfo("com.inactive.app", isActive = false)
        val unknown = appInfo("com.unknown.app", isActive = null)
        val h = harness(
            data = dataOf(active, inactive, unknown),
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.ACTIVE)),
            sort = SortSettings(mode = SortSettings.Mode.PACKAGENAME, reversed = false),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.active.app", "com.unknown.app")
    }

    @Test
    fun `combined filter USER+ENABLED keeps only user apps that are enabled`() = runTest2 {
        // Production default: FilterSettings = USER + ENABLED.
        val userEnabled = appInfo("com.userenabled.app", isSystem = false, isEnabled = true)
        val userDisabled = appInfo("com.userdisabled.app", isSystem = false, isEnabled = false)
        val systemEnabled = appInfo("com.android.systemenabled", isSystem = true, isEnabled = true)
        val systemDisabled = appInfo("com.android.systemdisabled", isSystem = true, isEnabled = false)
        val h = harness(
            data = dataOf(userEnabled, userDisabled, systemEnabled, systemDisabled),
            filter = FilterSettings(),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.userenabled.app")
    }

    // ─────────────────────────── search ───────────────────────────

    @Test
    fun `search filters by package name substring`() = runTest2 {
        val match = appInfo("com.alpha.app")
        val miss = appInfo("com.zebra.app")
        // Both are user+enabled so the default filter doesn't exclude them.
        val h = harness(data = dataOf(match, miss), filter = FilterSettings())

        h.vm.onSearchQueryChanged("alpha")
        advanceUntilIdle()

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.alpha.app")
    }

    @Test
    fun `search filters by label substring case-insensitively`() = runTest2 {
        val match = appInfo("com.first.app", label = "Photos")
        val miss = appInfo("com.second.app", label = "Calendar")
        val h = harness(data = dataOf(match, miss), filter = FilterSettings())

        h.vm.onSearchQueryChanged("PHOTO")
        advanceUntilIdle()

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.first.app")
    }

    @Test
    fun `empty search query yields all rows`() = runTest2 {
        val a = appInfo("com.a.app")
        val b = appInfo("com.b.app")
        val h = harness(data = dataOf(a, b), filter = FilterSettings())

        h.vm.onSearchQueryChanged("")
        advanceUntilIdle()

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName }.toSet() shouldBe setOf("com.a.app", "com.b.app")
    }

    // ─────────────────────────── sorting ───────────────────────────

    @Test
    fun `sort by NAME ascending sorts by label`() = runTest2 {
        val zebra = appInfo("com.z.app", label = "Zebra")
        val apple = appInfo("com.a.app", label = "Apple")
        val mango = appInfo("com.m.app", label = "Mango")
        val h = harness(
            data = dataOf(zebra, apple, mango),
            filter = FilterSettings(),
            sort = SortSettings(mode = SortSettings.Mode.NAME, reversed = false),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.a.app", "com.m.app", "com.z.app")
    }

    @Test
    fun `sort by NAME reversed flips the order`() = runTest2 {
        val zebra = appInfo("com.z.app", label = "Zebra")
        val apple = appInfo("com.a.app", label = "Apple")
        val h = harness(
            data = dataOf(zebra, apple),
            filter = FilterSettings(),
            sort = SortSettings(mode = SortSettings.Mode.NAME, reversed = true),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.z.app", "com.a.app")
    }

    @Test
    fun `sort by PACKAGENAME ascending sorts by package id`() = runTest2 {
        val a = appInfo("com.alpha.app", label = "Zzz")
        val b = appInfo("com.bravo.app", label = "Aaa")
        val h = harness(
            data = dataOf(b, a),
            filter = FilterSettings(),
            sort = SortSettings(mode = SortSettings.Mode.PACKAGENAME, reversed = false),
        )

        val rows = h.vm.state.first().rows!!
        rows.map { it.appInfo.pkg.packageName } shouldBe listOf("com.alpha.app", "com.bravo.app")
    }

    @Test
    fun `sort by SIZE falls back to default sort when size info is missing`() = runTest2 {
        // Regression: when sort.mode is SIZE but the current scan didn't load size info
        // (data.hasInfoSize=false), row ORDERING falls back to SortSettings() (LAST_UPDATE,
        // reversed=true) so rows aren't sorted by garbage 0L values. The displayed options keep
        // the requested sort — the sheet radio must not flap while a rescan is in flight.
        val newer = appInfo("com.newer.app", updatedAt = Instant.ofEpochSecond(2_000_000_000L))
        val older = appInfo("com.older.app", updatedAt = Instant.ofEpochSecond(1_000_000_000L))
        val h = harness(
            data = dataOf(newer, older),
            filter = FilterSettings(),
            sort = SortSettings(mode = SortSettings.Mode.SIZE, reversed = false),
            canInfoSize = false,
        )

        val state = h.vm.state.first()
        state.options.listSort shouldBe SortSettings(mode = SortSettings.Mode.SIZE, reversed = false)
        // Ordering fell back to LAST_UPDATE reversed=true → newer first.
        state.rows!!.map { it.appInfo.pkg.packageName } shouldBe listOf("com.newer.app", "com.older.app")
    }

    // ─────────────────────────── tap navigation ───────────────────────────

    @Test
    fun `onTapRow navigates to AppActionRoute`() = runTest2 {
        val app = appInfo("com.tapped.app")
        val h = harness(data = dataOf(app))
        val nav = collectNavEvents(h.vm)

        h.vm.onTapRow(app.installId)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe AppActionRoute(installId = app.installId)
        nav.cancel()
    }

    // ─────────────────────────── selection actions ───────────────────────────

    @Test
    fun `onToggleRequested empty set is a no-op`() = runTest2 {
        val h = harness(data = dataOf(appInfo("com.a.app")))
        val collected = collectEvents(h.vm)

        h.vm.onToggleRequested(emptySet())
        advanceUntilIdle()

        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onToggleRequested non-empty emits ConfirmToggle`() = runTest2 {
        val app = appInfo("com.a.app")
        val h = harness(data = dataOf(app))

        h.vm.onToggleRequested(setOf(app.installId))

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppControlListViewModel.Event.ConfirmToggle>()
        event.ids shouldBe setOf(app.installId)
    }

    @Test
    fun `onToggleConfirmed submits task with valid ids only and emits ShowResult`() = runTest2 {
        val live = appInfo("com.live.app")
        // Capture installId BEFORE the verify block: calling `live.installId` inside `coVerify`
        // would trigger MockK to count getter invocations as part of the verified call set.
        val liveInstallId = live.installId
        val staleId = InstallId(Pkg.Id("com.stale.app"), systemUserHandle)
        val h = harness(data = dataOf(live))

        val toggleResult = AppControlToggleTask.Result(
            enabled = setOf(liveInstallId),
            disabled = emptySet(),
            failed = emptySet(),
        )
        coEvery { h.taskSubmitter.submit(any<AppControlToggleTask>()) } returns toggleResult

        h.vm.onToggleConfirmed(setOf(liveInstallId, staleId))
        advanceUntilIdle()

        val expectedTask = AppControlToggleTask(targets = setOf(liveInstallId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
        // No extra submits (no init scan because data was provided).
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppControlListViewModel.Event.ShowResult>()
        event.result shouldBe toggleResult
    }

    @Test
    fun `onToggleConfirmed with all-stale ids does not submit`() = runTest2 {
        val live = appInfo("com.live.app")
        val staleId = InstallId(Pkg.Id("com.stale.app"), systemUserHandle)
        val h = harness(data = dataOf(live))

        h.vm.onToggleConfirmed(setOf(staleId))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onUninstallRequested empty set is a no-op`() = runTest2 {
        val h = harness(data = dataOf(appInfo("com.a.app")))
        val collected = collectEvents(h.vm)

        h.vm.onUninstallRequested(emptySet())
        advanceUntilIdle()

        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onUninstallConfirmed submits UninstallTask with valid ids`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(data = dataOf(app))

        val result = UninstallTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<UninstallTask>()) } returns result

        h.vm.onUninstallConfirmed(setOf(installId))
        advanceUntilIdle()

        val expectedTask = UninstallTask(targets = setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    @Test
    fun `onForceStopRequested with multi-select on free tier navigates to upgrade`() = runTest2 {
        // Multi-select force-stop is a Pro feature. Free users must hit the upgrade route
        // instead of being asked to confirm.
        val a = appInfo("com.a.app")
        val b = appInfo("com.b.app")
        val h = harness(data = dataOf(a, b), isPro = false)
        val nav = collectNavEvents(h.vm)
        val collected = collectEvents(h.vm)

        h.vm.onForceStopRequested(setOf(a.installId, b.installId))
        advanceUntilIdle()

        val navEvent = nav.list.single()
        navEvent.shouldBeInstanceOf<NavEvent.GoTo>()
        navEvent.destination shouldBe UpgradeRoute()
        // No confirm event emitted.
        collected.list shouldBe emptyList()
        nav.cancel()
        collected.cancel()
    }

    @Test
    fun `onForceStopRequested with single target on free tier emits ConfirmForceStop`() = runTest2 {
        // Single-target is allowed for free users.
        val a = appInfo("com.a.app")
        val h = harness(data = dataOf(a), isPro = false)

        h.vm.onForceStopRequested(setOf(a.installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppControlListViewModel.Event.ConfirmForceStop>()
        event.ids shouldBe setOf(a.installId)
    }

    @Test
    fun `onForceStopRequested with multi-select on Pro tier emits ConfirmForceStop`() = runTest2 {
        val a = appInfo("com.a.app")
        val b = appInfo("com.b.app")
        val h = harness(data = dataOf(a, b), isPro = true)

        h.vm.onForceStopRequested(setOf(a.installId, b.installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppControlListViewModel.Event.ConfirmForceStop>()
        event.ids shouldBe setOf(a.installId, b.installId)
    }

    @Test
    fun `onForceStopConfirmed submits ForceStopTask`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(data = dataOf(app), isPro = true)

        val result = ForceStopTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<ForceStopTask>()) } returns result

        h.vm.onForceStopConfirmed(setOf(installId))
        advanceUntilIdle()

        val expectedTask = ForceStopTask(targets = setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    @Test
    fun `onArchiveConfirmed submits ArchiveTask with valid ids`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(data = dataOf(app), isPro = true)

        val result = ArchiveTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<ArchiveTask>()) } returns result

        h.vm.onArchiveConfirmed(setOf(installId))
        advanceUntilIdle()

        val expectedTask = ArchiveTask(targets = setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    @Test
    fun `onRestoreConfirmed submits RestoreTask with valid ids`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(data = dataOf(app), isPro = true)

        val result = RestoreTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<RestoreTask>()) } returns result

        h.vm.onRestoreConfirmed(setOf(installId))
        advanceUntilIdle()

        val expectedTask = RestoreTask(targets = setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    // ─────────────────────────── exclude ───────────────────────────

    @Test
    fun `onExcludeSelected empty set is a no-op`() = runTest2 {
        val h = harness(data = dataOf(appInfo("com.a.app")))
        val collected = collectEvents(h.vm)

        h.vm.onExcludeSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.exclusionManager.save(any<Set<Exclusion>>()) }
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onExcludeSelected saves exclusions for valid ids only`() = runTest2 {
        val live = appInfo("com.live.app")
        val staleId = InstallId(Pkg.Id("com.stale.app"), systemUserHandle)
        val h = harness(data = dataOf(live))

        val savedExclusion = mockk<Exclusion>().apply { every { id } returns "exclusion-1" }
        val capturedSave = slot<Set<Exclusion>>()
        coEvery { h.exclusionManager.save(capture(capturedSave)) } returns listOf(savedExclusion)

        h.vm.onExcludeSelected(setOf(live.installId, staleId))
        advanceUntilIdle()

        capturedSave.captured.size shouldBe 1
    }

    @Test
    fun `onExcludeSelected event count reflects saved count not requested count`() = runTest2 {
        // When ExclusionManager.save() coalesces duplicates, the snackbar count should match
        // what was actually saved — not what was requested. Mirrors the CorpseFinder fix.
        val a = appInfo("com.a.app")
        val b = appInfo("com.b.app")
        val h = harness(data = dataOf(a, b))

        // Two ids selected, but only one exclusion actually saved.
        coEvery { h.exclusionManager.save(any<Set<Exclusion>>()) } returns listOf(
            mockk<Exclusion>().apply { every { id } returns "only-one-saved" },
        )

        h.vm.onExcludeSelected(setOf(a.installId, b.installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppControlListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 1
    }

    // ─────────────────────────── share ───────────────────────────

    @Test
    fun `onShareList with no matching ids emits no event`() = runTest2 {
        val h = harness(data = dataOf(appInfo("com.live.app")))
        val collected = collectEvents(h.vm)

        h.vm.onShareList(setOf(InstallId(Pkg.Id("com.stale.app"), systemUserHandle)))
        advanceUntilIdle()

        collected.list.filterIsInstance<AppControlListViewModel.Event.ShareList>() shouldBe emptyList()
        collected.cancel()
    }

    // ─────────────────────────── settings actions ───────────────────────────

    @Test
    fun `onSortDirectionToggle flips sort reversed flag`() = runTest2 {
        val h = harness(sort = SortSettings(reversed = false))

        h.vm.onSortDirectionToggle()
        advanceUntilIdle()

        val captured = slot<(SortSettings) -> SortSettings?>()
        coVerify(exactly = 1) { h.listSort.update(capture(captured)) }
        // Apply transformer to the starting value and check the flip.
        captured.captured(SortSettings(reversed = false))?.reversed shouldBe true
    }

    @Test
    fun `onTagsReset writes default FilterSettings`() = runTest2 {
        val h = harness(
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.SYSTEM)),
        )

        h.vm.onTagsReset()
        advanceUntilIdle()

        val captured = slot<(FilterSettings) -> FilterSettings?>()
        coVerify(exactly = 1) { h.listFilter.update(capture(captured)) }
        captured.captured(FilterSettings(tags = setOf(FilterSettings.Tag.SYSTEM))) shouldBe FilterSettings()
    }

    @Test
    fun `onTagToggle USER replaces SYSTEM if SYSTEM is active`() = runTest2 {
        val h = harness(
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.SYSTEM)),
        )

        h.vm.onTagToggle(FilterSettings.Tag.USER)
        advanceUntilIdle()

        val captured = slot<(FilterSettings) -> FilterSettings?>()
        coVerify(exactly = 1) { h.listFilter.update(capture(captured)) }
        val newFilter = captured.captured(FilterSettings(tags = setOf(FilterSettings.Tag.SYSTEM)))
        newFilter shouldBe FilterSettings(tags = setOf(FilterSettings.Tag.USER))
    }

    @Test
    fun `onTagToggle ENABLED replaces DISABLED if DISABLED is active`() = runTest2 {
        val h = harness(
            filter = FilterSettings(tags = setOf(FilterSettings.Tag.DISABLED)),
        )

        h.vm.onTagToggle(FilterSettings.Tag.ENABLED)
        advanceUntilIdle()

        val captured = slot<(FilterSettings) -> FilterSettings?>()
        coVerify(exactly = 1) { h.listFilter.update(capture(captured)) }
        val newFilter = captured.captured(FilterSettings(tags = setOf(FilterSettings.Tag.DISABLED)))
        newFilter shouldBe FilterSettings(tags = setOf(FilterSettings.Tag.ENABLED))
    }

    @Test
    fun `onAckSizeSortCaveat writes true`() = runTest2 {
        // `settings.ackSizeSortCaveat.value(true)` is the DataStoreValue.value(T) suspend extension,
        // which compiles to a call to `.update { true }`. Capture that update closure and verify it
        // returns true regardless of the prior stored value.
        val h = harness(ackSizeSortCaveatValue = false)

        h.vm.onAckSizeSortCaveat()
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.ackSizeSortCaveat.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    // ─────────────────────────── state capability flags ───────────────────────────

    @Test
    fun `state surfaces capability flags from AppControl state`() = runTest2 {
        val h = harness(
            canToggle = false,
            canForceStop = false,
            canArchive = true,
            canRestore = true,
            canInfoActive = false,
            canInfoSize = false,
            canInfoScreenTime = false,
        )

        val state = h.vm.state.first()
        state.allowActionToggle shouldBe false
        state.allowActionForceStop shouldBe false
        state.allowActionArchive shouldBe true
        state.allowActionRestore shouldBe true
        state.allowFilterActive shouldBe false
    }

    @Test
    fun `state allowFilterActive requires both canInfoActive and moduleActivityEnabled`() = runTest2 {
        // Activity filter only visible when BOTH the device reports it can (canInfoActive) AND the
        // user has enabled the module setting. Either being false hides the filter.
        val onlyCap = harness(
            canInfoActive = true,
            activityEnabled = false,
        )
        onlyCap.vm.state.first().allowFilterActive shouldBe false

        val onlyEnabled = harness(
            canInfoActive = false,
            activityEnabled = true,
        )
        onlyEnabled.vm.state.first().allowFilterActive shouldBe false

        val both = harness(
            canInfoActive = true,
            activityEnabled = true,
        )
        both.vm.state.first().allowFilterActive shouldBe true
    }

    @Test
    fun `state sizeSortModuleEnabled mirrors the module setting only`() = runTest2 {
        // Permission state must NOT disable the size sort row anymore — tapping it with missing
        // setup shows the setup dialog instead. Only the explicit module toggle disables it.
        val moduleOff = harness(
            canInfoSize = true,
            sizingEnabled = false,
        )
        moduleOff.vm.state.first().sizeSortModuleEnabled shouldBe false

        val moduleOnNoPermission = harness(
            canInfoSize = false,
            sizingEnabled = true,
        )
        moduleOnNoPermission.vm.state.first().sizeSortModuleEnabled shouldBe true
    }

    // ─────────────────────────── setup-gated sort modes ───────────────────────────

    @Test
    fun `selecting SCREEN_TIME with missing usage setup emits IncompleteSetupException and does not persist`() = runTest2 {
        val h = harness(missingSetup = setOf(SetupModule.Type.USAGE_STATS))
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.errorEvents.collect { errors.add(it) }
        }

        h.vm.onSortModeChanged(SortSettings.Mode.SCREEN_TIME)
        advanceUntilIdle()

        val error = errors.single()
        error.shouldBeInstanceOf<IncompleteSetupException>()
        error.setupTypes shouldBe setOf(SetupModule.Type.USAGE_STATS)
        h.listSortBacking.value shouldBe SortSettings()
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        errorJob.cancel()
    }

    @Test
    fun `selecting SIZE with only storage missing emits exactly the missing subset`() = runTest2 {
        val h = harness(missingSetup = setOf(SetupModule.Type.STORAGE))
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.errorEvents.collect { errors.add(it) }
        }

        h.vm.onSortModeChanged(SortSettings.Mode.SIZE)
        advanceUntilIdle()

        val error = errors.single()
        error.shouldBeInstanceOf<IncompleteSetupException>()
        error.setupTypes shouldBe setOf(SetupModule.Type.STORAGE)
        h.listSortBacking.value shouldBe SortSettings()
        errorJob.cancel()
    }

    @Test
    fun `selecting SIZE while sizing module is disabled is a no-op`() = runTest2 {
        // Defence-in-depth: the row is disabled in the sheet, but a direct call must neither
        // persist the sort nor show a setup dialog (the module being off is a user choice).
        val h = harness(
            sizingEnabled = false,
            missingSetup = setOf(SetupModule.Type.USAGE_STATS, SetupModule.Type.STORAGE),
        )
        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            h.vm.errorEvents.collect { errors.add(it) }
        }

        h.vm.onSortModeChanged(SortSettings.Mode.SIZE)
        advanceUntilIdle()

        errors shouldBe emptyList()
        h.listSortBacking.value shouldBe SortSettings()
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        errorJob.cancel()
    }

    @Test
    fun `selecting SCREEN_TIME with setup complete persists sort and submits scan loading usage`() = runTest2 {
        // Order matters: the sort must be persisted BEFORE refresh(), because buildScanTask()
        // reads the persisted mode to decide loadInfoScreenTime.
        val h = harness(missingSetup = emptySet())

        h.vm.onSortModeChanged(SortSettings.Mode.SCREEN_TIME)
        advanceUntilIdle()

        h.listSortBacking.value.mode shouldBe SortSettings.Mode.SCREEN_TIME
        val task = slot<AppControlScanTask>()
        coVerify(exactly = 1) { h.taskSubmitter.submit(capture(task)) }
        task.captured.loadInfoScreenTime shouldBe true
    }

    @Test
    fun `onScreenResume refreshes permission-backed setup modules`() = runTest2 {
        // Setup-module permission state is cached; resuming the screen must re-check it so the
        // setup-required gate doesn't decide on stale state (e.g. permission granted in system
        // settings while this screen was in the background).
        val h = harness()

        h.vm.onScreenResume()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.usageStatsSetupModule.refresh() }
        coVerify(exactly = 1) { h.storageSetupModule.refresh() }
    }

    @Test
    fun `persisted sort is reset when its required setup becomes missing`() = runTest2 {
        // E.g. usage access revoked while sorted by screen time: without the reset the list
        // stays wedged on a sort mode whose data can never load.
        val h = harness(
            sort = SortSettings(mode = SortSettings.Mode.SCREEN_TIME),
            missingSetup = setOf(SetupModule.Type.USAGE_STATS),
        )
        advanceUntilIdle()

        h.listSortBacking.value shouldBe SortSettings()
    }

    @Test
    fun `persisted SCREEN_TIME sort orders rows by default sort when scan lacks usage data`() = runTest2 {
        // canInfoScreenTime=true (permission fine) but the current scan didn't load usage —
        // ordering must not use garbage null durations. Displayed options keep the requested
        // sort so the UI doesn't flap during the rescan that onSortModeChanged triggers.
        val newer = appInfo("com.newer.app", updatedAt = Instant.ofEpochSecond(2_000_000_000L))
        val older = appInfo("com.older.app", updatedAt = Instant.ofEpochSecond(1_000_000_000L))
        val h = harness(
            data = dataOf(newer, older),
            sort = SortSettings(mode = SortSettings.Mode.SCREEN_TIME, reversed = false),
            canInfoScreenTime = true,
        )

        val state = h.vm.state.first()
        state.options.listSort shouldBe SortSettings(mode = SortSettings.Mode.SCREEN_TIME, reversed = false)
        state.rows!!.map { it.appInfo.pkg.packageName } shouldBe listOf("com.newer.app", "com.older.app")
    }
}
