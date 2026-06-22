package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.appcontrol.core.archive.ArchiveSupport
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopper
import eu.darken.sdmse.appcontrol.core.restore.Restorer
import eu.darken.sdmse.appcontrol.core.toggle.ComponentToggler
import eu.darken.sdmse.appcontrol.core.uninstall.Uninstaller
import eu.darken.sdmse.appcontrol.core.archive.Archiver
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant
import javax.inject.Provider

class AppControlTest : BaseTest() {

    // AppControl wraps work in keepResourceHoldersAlive(appScan), which calls
    // addChild(sharedResource) + sharedResource.get() on it. Plain MockK mocks fail at those
    // calls — so we wire appScan to a real SharedResource.createKeepAlive(...) backed by a
    // long-lived scope. Mirrors the pattern in CorpseFinderTest.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private val systemUserHandle = UserHandle2(handleId = 0)

    private fun setupModuleStateCurrent(complete: Boolean): SetupModule.State.Current =
        object : SetupModule.State.Current {
            override val type = SetupModule.Type.INVENTORY
            override val isComplete: Boolean = complete
        }

    private fun fakeSetupModule(type: SetupModule.Type, complete: Boolean): SetupModule =
        mockk<SetupModule>().apply {
            // state is a Flow<State>; the .first() lookup walks .filterIsInstance<Current>()
            // before reading isComplete. Emit a Current with the requested completion.
            every { state } returns flowOf(
                object : SetupModule.State.Current {
                    override val type = type
                    override val isComplete: Boolean = complete
                },
            )
            coJustRun { refresh() }
        }

    private class Setup(
        val appControl: AppControl,
        val appScan: AppScan,
    )

    private fun setupAppControl(
        canInfoActive: Boolean = true,
        canInfoSize: Boolean = true,
        canInfoScreenTime: Boolean = true,
        useAcs: Boolean = false,
        useRoot: Boolean = false,
        useAdb: Boolean = false,
        archiveEnabled: Boolean = false,
        appsReturnedByScan: Set<AppInfo> = emptySet(),
        usageStatsSetupModuleOverride: SetupModule? = null,
        storageSetupModuleOverride: SetupModule? = null,
    ): Setup {
        val appScan = mockk<AppScan>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("appScan", keepAliveScope)
            coEvery { allApps(any(), any(), any(), any()) } returns appsReturnedByScan
            coJustRun { refresh() }
        }

        // Production: canInfoActive = usageStatsSetupModule.isComplete OR useRoot OR useAdb.
        // Drive it through the setup module so the production combine still wires up the same way.
        val usageStatsSetupModule = usageStatsSetupModuleOverride
            ?: fakeSetupModule(SetupModule.Type.USAGE_STATS, canInfoActive)
        // canInfoSize = usageStatsSetupModule.isComplete AND storageSetupModule.isComplete.
        // Both must be true for canInfoSize=true. usage is already set above; storage must match
        // canInfoSize to control the AND product. When `canInfoSize` is requested true but the
        // usage flag is false, the AND fails and canInfoSize ends up false — that's intentional;
        // we tighten the test fixture to match the production formula.
        val storageSetupModule = storageSetupModuleOverride
            ?: fakeSetupModule(SetupModule.Type.STORAGE, canInfoSize)
        val appInventorySetupModule = fakeSetupModule(SetupModule.Type.INVENTORY, complete = true)

        val automationSubmitter = mockk<AutomationSubmitter>().apply {
            every { this@apply.useAcs } returns flowOf(useAcs)
        }
        val rootManager = mockk<RootManager>().apply {
            every { this@apply.useRoot } returns flowOf(useRoot)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { this@apply.useAdb } returns flowOf(useAdb)
        }

        val archiveSupport = mockk<ArchiveSupport>().apply {
            every { isArchivingEnabled } returns archiveEnabled
        }

        val userManager = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns UserProfile2(handle = systemUserHandle)
        }

        val componentToggler = mockk<ComponentToggler>(relaxed = true)
        val forceStopper = mockk<ForceStopper>(relaxed = true)
        val uninstaller = mockk<Uninstaller>(relaxed = true)
        val archiver = mockk<Archiver>(relaxed = true)
        val restorer = mockk<Restorer>(relaxed = true)
        val appExporterProvider = mockk<Provider<AppExporter>>(relaxed = true)

        val appControl = AppControl(
            appScope = keepAliveScope,
            userManager = userManager,
            componentToggler = componentToggler,
            forceStopper = forceStopper,
            uninstaller = uninstaller,
            archiver = archiver,
            restorer = restorer,
            archiveSupport = archiveSupport,
            usageStatsSetupModule = usageStatsSetupModule,
            storageSetupModule = storageSetupModule,
            rootManager = rootManager,
            adbManager = adbManager,
            appExporterProvider = appExporterProvider,
            appInventorySetupModule = appInventorySetupModule,
            automationManager = automationSubmitter,
            appScan = appScan,
        )
        return Setup(appControl = appControl, appScan = appScan)
    }

    private fun buildScanTask(
        loadInfoSize: Boolean = false,
        loadInfoActive: Boolean = false,
        loadInfoScreenTime: Boolean = false,
        includeMultiUser: Boolean = false,
        refreshPkgCache: Boolean = false,
    ) = AppControlScanTask(
        refreshPkgCache = refreshPkgCache,
        loadInfoSize = loadInfoSize,
        loadInfoActive = loadInfoActive,
        loadInfoScreenTime = loadInfoScreenTime,
        includeMultiUser = includeMultiUser,
    )

    private suspend fun AppControl.dataFromState(): AppControl.Data? =
        state.map { it.data }.first()

    // ─────────────────────────── hasInfoActive regression ───────────────────────────

    @Test
    fun `performScan hasInfoActive is true when loadInfoActive and canInfoActive both true`() = runTest2 {
        // Regression test for the fixed copy-paste bug at AppControl.kt:174 — hasInfoActive used
        // to read `loadInfoSize && canInfoSize`. This test pins the correct formula:
        // hasInfoActive = loadInfoActive && canInfoActive.
        val setup = setupAppControl(canInfoActive = true, canInfoSize = true)

        setup.appControl.submit(buildScanTask(loadInfoActive = true))

        val data = setup.appControl.dataFromState()!!
        data.hasInfoActive shouldBe true
    }

    @Test
    fun `performScan hasInfoActive is false when loadInfoActive is true but canInfoActive is false`() = runTest2 {
        // Capability gate: even if the task asks for active info, the device must be able to
        // provide it. Without canInfoActive, hasInfoActive must be false regardless of inputs.
        val setup = setupAppControl(canInfoActive = false, canInfoSize = true)

        setup.appControl.submit(buildScanTask(loadInfoActive = true))

        val data = setup.appControl.dataFromState()!!
        data.hasInfoActive shouldBe false
    }

    @Test
    fun `performScan hasInfoActive is false when loadInfoActive is false`() = runTest2 {
        val setup = setupAppControl(canInfoActive = true, canInfoSize = true)

        setup.appControl.submit(buildScanTask(loadInfoActive = false))

        val data = setup.appControl.dataFromState()!!
        data.hasInfoActive shouldBe false
    }

    @Test
    fun `performScan hasInfoActive ignores size inputs entirely`() = runTest2 {
        // The bug: hasInfoActive used to be `loadInfoSize && canInfoSize`. Two regression-shaped
        // cases that would have failed under the bug:
        //
        //   1) loadInfoSize=true,  loadInfoActive=false → bug: hasInfoActive=true (wrong)
        //   2) loadInfoSize=false, loadInfoActive=true  → bug: hasInfoActive=false (wrong)
        //
        // Both must now follow the loadInfoActive input.
        val sizeOnlySetup = setupAppControl(canInfoActive = true, canInfoSize = true)
        sizeOnlySetup.appControl.submit(
            buildScanTask(loadInfoSize = true, loadInfoActive = false),
        )
        // Case 1: size requested, active not requested → hasInfoActive must be false even though
        // size flags are true.
        sizeOnlySetup.appControl.dataFromState()!!.hasInfoActive shouldBe false

        val activeOnlySetup = setupAppControl(canInfoActive = true, canInfoSize = true)
        activeOnlySetup.appControl.submit(
            buildScanTask(loadInfoSize = false, loadInfoActive = true),
        )
        // Case 2: active requested, size not requested → hasInfoActive must be true even though
        // size flags are false.
        activeOnlySetup.appControl.dataFromState()!!.hasInfoActive shouldBe true
    }

    // ─────────────────────────── neighbour fields ───────────────────────────

    @Test
    fun `performScan hasInfoSize follows loadInfoSize AND canInfoSize`() = runTest2 {
        // Sanity check on the field that was the source of the bug — its own formula must
        // still be the correct one.
        val setup = setupAppControl(canInfoActive = true, canInfoSize = true)

        setup.appControl.submit(buildScanTask(loadInfoSize = true))

        setup.appControl.dataFromState()!!.hasInfoSize shouldBe true
    }

    @Test
    fun `performScan hasInfoScreenTime follows loadInfoScreenTime AND canInfoScreenTime`() = runTest2 {
        val setup = setupAppControl(canInfoActive = true, canInfoSize = true)

        setup.appControl.submit(buildScanTask(loadInfoScreenTime = true))

        setup.appControl.dataFromState()!!.hasInfoScreenTime shouldBe true
    }

    @Test
    fun `performScan hasIncludedMultiUser follows includeMultiUser AND canIncludeMultiUser`() = runTest2 {
        // canIncludeMultiUser = useRoot || useAdb. Without either, hasIncludedMultiUser must be
        // false even when the task requested includeMultiUser.
        val noPriv = setupAppControl(useRoot = false, useAdb = false)
        noPriv.appControl.submit(buildScanTask(includeMultiUser = true))
        noPriv.appControl.dataFromState()!!.hasIncludedMultiUser shouldBe false

        val rooted = setupAppControl(useRoot = true)
        rooted.appControl.submit(buildScanTask(includeMultiUser = true))
        rooted.appControl.dataFromState()!!.hasIncludedMultiUser shouldBe true
    }

    // ─────────────────────────── scan returns Result with item count ───────────────────────────

    @Test
    fun `performScan returns a Result reflecting the AppInfo set size`() = runTest2 {
        val pretendApps = setOf(
            mockk<AppInfo>(relaxed = true),
            mockk<AppInfo>(relaxed = true),
            mockk<AppInfo>(relaxed = true),
        )
        val setup = setupAppControl(appsReturnedByScan = pretendApps)

        val result = setup.appControl.submit(buildScanTask())

        result.shouldBeInstanceOf<AppControlScanTask.Result>()
        setup.appControl.dataFromState()!!.apps.size shouldBe 3
    }

    // ─────────────────────────── scan delegates to AppScan with computed flags ───────────────────────────

    @Test
    fun `performScan passes effective canInfoActive into AppScan allApps`() = runTest2 {
        // The actual loader call must receive the AND of the task flag and the device capability,
        // not the raw task flag. This test would catch a regression that forwards `task.loadInfoActive`
        // directly (bypassing the capability gate) — symmetric to the hasInfoActive bug surface.
        val setup = setupAppControl(canInfoActive = false)

        val includeActiveSlot = slot<Boolean>()
        coEvery {
            setup.appScan.allApps(
                user = any(),
                includeUsage = any(),
                includeActive = capture(includeActiveSlot),
                includeSize = any(),
            )
        } returns emptySet()

        setup.appControl.submit(buildScanTask(loadInfoActive = true))

        // canInfoActive=false → AND collapses to false.
        includeActiveSlot.captured shouldBe false
    }

    // ─────────────────────────── missingSetup derivation ───────────────────────────

    private fun loadingSetupModule(type: SetupModule.Type): SetupModule = mockk<SetupModule>().apply {
        every { state } returns flowOf(
            object : SetupModule.State.Loading {
                override val type = type
                override val startAt: Instant = Instant.EPOCH
            },
        )
        coJustRun { refresh() }
    }

    @Test
    fun `missingSetup is empty when all modules are complete`() = runTest2 {
        val setup = setupAppControl(canInfoActive = true, canInfoSize = true)

        setup.appControl.state.first().missingSetup shouldBe emptySet()
    }

    @Test
    fun `missingSetup lists incomplete Current modules`() = runTest2 {
        val setup = setupAppControl(
            usageStatsSetupModuleOverride = fakeSetupModule(SetupModule.Type.USAGE_STATS, complete = false),
            storageSetupModuleOverride = fakeSetupModule(SetupModule.Type.STORAGE, complete = true),
        )

        setup.appControl.state.first().missingSetup shouldBe setOf(SetupModule.Type.USAGE_STATS)

        val both = setupAppControl(
            usageStatsSetupModuleOverride = fakeSetupModule(SetupModule.Type.USAGE_STATS, complete = false),
            storageSetupModuleOverride = fakeSetupModule(SetupModule.Type.STORAGE, complete = false),
        )

        both.appControl.state.first().missingSetup shouldBe setOf(
            SetupModule.Type.USAGE_STATS,
            SetupModule.Type.STORAGE,
        )
    }

    @Test
    fun `missingSetup ignores modules that are still Loading`() = runTest2 {
        // Loading must not count as missing, otherwise the UI would flash setup-required
        // dialogs during startup before the module state has resolved.
        val setup = setupAppControl(
            usageStatsSetupModuleOverride = loadingSetupModule(SetupModule.Type.USAGE_STATS),
            storageSetupModuleOverride = fakeSetupModule(SetupModule.Type.STORAGE, complete = false),
        )

        setup.appControl.state.first().missingSetup shouldBe setOf(SetupModule.Type.STORAGE)
    }
}
