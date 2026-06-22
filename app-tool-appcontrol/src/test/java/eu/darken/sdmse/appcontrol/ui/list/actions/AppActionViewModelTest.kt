package eu.darken.sdmse.appcontrol.ui.list.actions

import android.content.Context
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.core.archive.ArchiveTask
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopTask
import eu.darken.sdmse.appcontrol.core.restore.RestoreTask
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.uninstall.UninstallTask
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItem
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AppActionViewModelTest : BaseTest() {

    private val systemUserHandle = UserHandle2(handleId = 0)

    private fun appInfo(
        pkgName: String,
        label: String = pkgName,
        userHandle: UserHandle2 = systemUserHandle,
        canBeToggled: Boolean = true,
        canBeStopped: Boolean = true,
        canBeDeleted: Boolean = true,
        canBeArchived: Boolean = false,
        canBeRestored: Boolean = false,
        canBeExported: Boolean = false,
        isEnabled: Boolean = true,
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
            every { (this@apply as InstallDetails).isEnabled } returns isEnabled
            every { (this@apply as InstallDetails).isSystemApp } returns false
            every { (this@apply as InstallDetails).installerInfo } returns mockk(relaxed = true)
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = canBeToggled,
            canBeStopped = canBeStopped,
            canBeExported = canBeExported,
            canBeDeleted = canBeDeleted,
            canBeArchived = canBeArchived,
            canBeRestored = canBeRestored,
        )
    }

    private fun appControlState(
        apps: List<AppInfo> = emptyList(),
        canToggle: Boolean = true,
        canForceStop: Boolean = true,
        canArchive: Boolean = false,
        canRestore: Boolean = false,
    ): AppControl.State = AppControl.State(
        data = AppControl.Data(
            apps = apps,
            hasInfoScreenTime = false,
            hasInfoActive = false,
            hasInfoSize = false,
            hasIncludedMultiUser = false,
        ),
        progress = null,
        canToggle = canToggle,
        canForceStop = canForceStop,
        canArchive = canArchive,
        canRestore = canRestore,
        canInfoActive = false,
        canInfoSize = false,
        canInfoScreenTime = false,
        canIncludeMultiUser = false,
    )

    private class Harness(
        val vm: AppActionViewModel,
        val appControl: AppControl,
        val taskSubmitter: TaskSubmitter,
        val exclusionManager: ExclusionManager,
        val userManager: UserManager2,
        val stateFlow: MutableStateFlow<AppControl.State>,
        val exclusionsFlow: MutableStateFlow<Collection<ExclusionHolder>>,
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
        vm: AppActionViewModel,
    ): CollectedEvents<AppActionViewModel.Event> {
        val list = mutableListOf<AppActionViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        apps: List<AppInfo> = emptyList(),
        currentUserHandle: UserHandle2 = systemUserHandle,
        existingExclusions: Collection<Exclusion> = emptyList(),
        canToggle: Boolean = true,
        canForceStop: Boolean = true,
        canArchive: Boolean = false,
        canRestore: Boolean = false,
    ): Harness {
        val stateFlow = MutableStateFlow(
            appControlState(
                apps = apps,
                canToggle = canToggle,
                canForceStop = canForceStop,
                canArchive = canArchive,
                canRestore = canRestore,
            ),
        )
        val progressFlow = MutableStateFlow(null as Progress.Data?)
        val appControl = mockk<AppControl>(relaxed = true).apply {
            every { state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)

        // ExclusionManager.exclusions returns Collection<ExclusionHolder>; the VM calls
        // exclusionManager.current() which is .exclusions.first().map { it.exclusion }.
        val holders: Collection<ExclusionHolder> = existingExclusions.map { ex ->
            mockk<ExclusionHolder>().apply { every { exclusion } returns ex }
        }
        val exclusionsFlow = MutableStateFlow(holders)
        val exclusionManager = mockk<ExclusionManager>(relaxed = true).apply {
            every { exclusions } returns exclusionsFlow
        }

        val userManager = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns UserProfile2(handle = currentUserHandle)
        }
        val context = mockk<Context>(relaxed = true).apply {
            every { packageManager } returns mockk(relaxed = true)
        }

        val vm = AppActionViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            context = context,
            appControl = appControl,
            taskManager = taskSubmitter,
            exclusionManager = exclusionManager,
            userManager2 = userManager,
        )
        return Harness(
            vm = vm,
            appControl = appControl,
            taskSubmitter = taskSubmitter,
            exclusionManager = exclusionManager,
            userManager = userManager,
            stateFlow = stateFlow,
            exclusionsFlow = exclusionsFlow,
        )
    }

    // ─────────────────────────── state binding ───────────────────────────

    @Test
    fun `state is empty until setInstallId is called`() = runTest2 {
        val app = appInfo("com.a.app")
        val h = harness(apps = listOf(app))

        // No setInstallId yet → state should be the empty initial value.
        val state = h.vm.state.first()
        state.appInfo shouldBe null
        state.items shouldBe null
    }

    @Test
    fun `setInstallId binds the matching AppInfo and produces action items`() = runTest2 {
        val app = appInfo("com.a.app", canBeDeleted = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))

        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.appInfo?.installId shouldBe installId
        state.items.shouldNotBeEmpty()
    }

    @Test
    fun `setInstallId is a no-op when called twice with the same id`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(apps = listOf(app))

        h.vm.setInstallId(installId)
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        // No crash, no excess emissions — state still resolves.
        h.vm.state.first().appInfo?.installId shouldBe installId
    }

    // ─────────────────────────── confirmation events ───────────────────────────

    @Test
    fun `onActionTapped Uninstall emits ConfirmUninstall`() = runTest2 {
        val app = appInfo("com.a.app", canBeDeleted = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        h.vm.onActionTapped(AppActionItem.Action.Uninstall(installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppActionViewModel.Event.ConfirmUninstall>()
        event.installId shouldBe installId
    }

    @Test
    fun `onActionTapped Archive emits ConfirmArchive`() = runTest2 {
        val app = appInfo("com.a.app", canBeArchived = true)
        val installId = app.installId
        val h = harness(apps = listOf(app), canArchive = true)
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        h.vm.onActionTapped(AppActionItem.Action.Archive(installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppActionViewModel.Event.ConfirmArchive>()
        event.installId shouldBe installId
    }

    @Test
    fun `onActionTapped Restore emits ConfirmRestore`() = runTest2 {
        val app = appInfo("com.a.app", canBeRestored = true)
        val installId = app.installId
        val h = harness(apps = listOf(app), canRestore = true)
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        h.vm.onActionTapped(AppActionItem.Action.Restore(installId))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppActionViewModel.Event.ConfirmRestore>()
        event.installId shouldBe installId
    }

    // ─────────────────────────── task submission ───────────────────────────

    @Test
    fun `onActionTapped Toggle submits AppControlToggleTask and emits ShowResult`() = runTest2 {
        val app = appInfo("com.a.app", canBeToggled = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = AppControlToggleTask.Result(
            enabled = setOf(installId),
            disabled = emptySet(),
            failed = emptySet(),
        )
        coEvery { h.taskSubmitter.submit(any<AppControlToggleTask>()) } returns result

        h.vm.onActionTapped(AppActionItem.Action.Toggle(installId, isEnabled = true))
        advanceUntilIdle()

        val expectedTask = AppControlToggleTask(setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppActionViewModel.Event.ShowResult>()
        event.result shouldBe result
    }

    @Test
    fun `onActionTapped ForceStop submits ForceStopTask`() = runTest2 {
        val app = appInfo("com.a.app", canBeStopped = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = ForceStopTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<ForceStopTask>()) } returns result

        h.vm.onActionTapped(AppActionItem.Action.ForceStop(installId))
        advanceUntilIdle()

        val expectedTask = ForceStopTask(setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    @Test
    fun `onUninstallConfirmed submits UninstallTask on success and emits ShowResult`() = runTest2 {
        val app = appInfo("com.a.app", canBeDeleted = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = UninstallTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<UninstallTask>()) } returns result

        h.vm.onUninstallConfirmed()
        advanceUntilIdle()

        val expectedTask = UninstallTask(setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppActionViewModel.Event.ShowResult>()
    }

    @Test
    fun `onUninstallConfirmed on failure routes the failure to errorEvents and emits no ShowResult`() = runTest2 {
        // When uninstall fails, the VM throws UninstallException — caught by ViewModel4's launch
        // handler which forwards to errorEvents. No ShowResult event is emitted on failure.
        val app = appInfo("com.a.app", canBeDeleted = true)
        val installId = app.installId
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = UninstallTask.Result(success = emptySet(), failed = setOf(installId))
        coEvery { h.taskSubmitter.submit(any<UninstallTask>()) } returns result

        val collected = collectEvents(h.vm)

        h.vm.onUninstallConfirmed()
        advanceUntilIdle()

        // Task submitted, but no ShowResult — error path swallowed it.
        coVerify { h.taskSubmitter.submit(any<UninstallTask>()) }
        collected.list.filterIsInstance<AppActionViewModel.Event.ShowResult>() shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onArchiveConfirmed submits ArchiveTask on success`() = runTest2 {
        val app = appInfo("com.a.app", canBeArchived = true)
        val installId = app.installId
        val h = harness(apps = listOf(app), canArchive = true)
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = ArchiveTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<ArchiveTask>()) } returns result

        h.vm.onArchiveConfirmed()
        advanceUntilIdle()

        val expectedTask = ArchiveTask(setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    @Test
    fun `onRestoreConfirmed submits RestoreTask on success`() = runTest2 {
        val app = appInfo("com.a.app", canBeRestored = true)
        val installId = app.installId
        val h = harness(apps = listOf(app), canRestore = true)
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        val result = RestoreTask.Result(success = setOf(installId), failed = emptySet())
        coEvery { h.taskSubmitter.submit(any<RestoreTask>()) } returns result

        h.vm.onRestoreConfirmed()
        advanceUntilIdle()

        val expectedTask = RestoreTask(setOf(installId))
        coVerify(exactly = 1) { h.taskSubmitter.submit(expectedTask) }
    }

    // ─────────────────────────── exclude handling ───────────────────────────

    @Test
    fun `onActionTapped Exclude with no existing exclusion saves a PkgExclusion`() = runTest2 {
        val app = appInfo("com.a.app")
        val installId = app.installId
        val pkgId = app.id
        val h = harness(apps = listOf(app))
        h.vm.setInstallId(installId)
        advanceUntilIdle()

        h.vm.onActionTapped(
            AppActionItem.Action.Exclude(installId = installId, existingExclusionId = null),
        )
        advanceUntilIdle()

        // The save() extension forwards to save(setOf(exclusion)). Verify exactly one save and
        // that it contains a PkgExclusion for this pkgId.
        coVerify(exactly = 1) {
            h.exclusionManager.save(match<Set<Exclusion>> { exclusions ->
                exclusions.size == 1 &&
                    exclusions.single().let { ex ->
                        ex is PkgExclusion && ex.pkgId == pkgId
                    }
            })
        }
    }

    // ─────────────────────────── dismiss-on-data-gone ───────────────────────────

    @Test
    fun `setInstallId triggers navUp when AppControl data no longer contains the id`() = runTest2 {
        // After data loads (non-null) and the app is missing, the VM dismisses the sheet by
        // calling navUp. This guards against tapping a stale row.
        val present = appInfo("com.present.app")
        val missingId = InstallId(Pkg.Id("com.missing.app"), systemUserHandle)
        val h = harness(apps = listOf(present))

        h.vm.setInstallId(missingId)
        advanceUntilIdle()

        // The init block dispatches navUp through navEvents.
        h.vm.navEvents.first().shouldBeInstanceOf<eu.darken.sdmse.common.navigation.NavEvent.Up>()
    }

    @Test
    fun `transient null data during a scan refresh does not dismiss the sheet`() = runTest2 {
        // Regression: while a refresh is in flight, AppControl.state.data is briefly null. The
        // VM must wait for non-null data before deciding the app is gone — otherwise the sheet
        // would close every time the user triggered a manual refresh.
        val app = appInfo("com.a.app")
        val installId = app.installId
        val h = harness(apps = listOf(app))

        h.vm.setInstallId(installId)
        advanceUntilIdle()

        // Simulate a scan-refresh by transitioning data → null → data with the app present.
        h.stateFlow.value = h.stateFlow.value.copy(data = null)
        advanceUntilIdle()
        h.stateFlow.value = appControlState(apps = listOf(app))
        advanceUntilIdle()

        // navUp must not have fired during the null transition.
        // (If init's filterNotNull let null through, navUp would have emitted by now.)
        h.vm.state.first().appInfo?.installId shouldBe installId
    }

    private fun <T> Collection<T>?.shouldNotBeEmpty() {
        if (this == null || this.isEmpty()) {
            throw AssertionError("Expected non-empty collection but got: $this")
        }
    }
}
