package eu.darken.sdmse.appcontrol.core

import android.content.Context
import android.content.res.Resources
import eu.darken.sdmse.appcontrol.core.archive.ArchiveSupport
import eu.darken.sdmse.appcontrol.core.export.AppExportTask
import eu.darken.sdmse.appcontrol.core.export.AppExporter
import eu.darken.sdmse.appcontrol.core.forcestop.ForceStopper
import eu.darken.sdmse.appcontrol.core.restore.Restorer
import eu.darken.sdmse.appcontrol.core.toggle.AppControlToggleTask
import eu.darken.sdmse.appcontrol.core.toggle.ComponentToggler
import eu.darken.sdmse.appcontrol.core.uninstall.Uninstaller
import eu.darken.sdmse.appcontrol.core.archive.Archiver
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
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
        val componentToggler: ComponentToggler,
    )

    private fun setupAppControl(
        appExporter: AppExporter? = null,
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

        val componentToggler = mockk<ComponentToggler>(relaxed = true).apply {
            // A relaxed mock would swallow the lambda, and with it the whole toggle loop.
            coEvery { useRes<Unit>(any()) } coAnswers { firstArg<suspend (Any) -> Unit>().invoke(Unit) }
        }
        val forceStopper = mockk<ForceStopper>(relaxed = true)
        val uninstaller = mockk<Uninstaller>(relaxed = true)
        val archiver = mockk<Archiver>(relaxed = true)
        val restorer = mockk<Restorer>(relaxed = true)
        val appExporterProvider = mockk<Provider<AppExporter>>(relaxed = true).apply {
            appExporter?.let { every { get() } returns it }
        }

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
        return Setup(appControl = appControl, appScan = appScan, componentToggler = componentToggler)
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

    // ─────────────────────────── export batch resilience ───────────────────────────

    private fun installedApp(pkgName: String): AppInfo {
        val pkg = mockk<Installed>(relaxed = true).apply {
            every { id } returns Pkg.Id(pkgName)
            every { packageName } returns pkgName
            every { label } returns null
            every { userHandle } returns systemUserHandle
            every { installId } returns InstallId(Pkg.Id(pkgName), systemUserHandle)
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = false,
            canBeStopped = false,
            canBeExported = true,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = false,
        )
    }

    @Test
    fun `performExportSave records a target that is missing from the snapshot as failed`() = runTest2 {
        // A refresh or an uninstall between scan and export removes the target from the snapshot. That
        // lookup used to sit outside the try, so it tore down the batch and discarded finished exports.
        val first = installedApp("eu.thlab.first")
        val third = installedApp("eu.thlab.third")
        val goneId = InstallId(Pkg.Id("eu.thlab.gone"), systemUserHandle)

        lateinit var appControl: AppControl
        val progressAtSave = mutableListOf<Long>()
        val exporter = mockk<AppExporter>().apply {
            every { progress } returns flowOf(null)
            coEvery { save(any(), any()) } coAnswers {
                progressAtSave.add(appControl.progress.first()?.count?.current ?: -1L)
                AppExporter.Result(
                    installId = firstArg<AppInfo>().installId,
                    baseApk = null,
                    extraSources = null,
                    savePath = mockk(),
                    exportSize = 1L,
                )
            }
        }

        val setup = setupAppControl(appExporter = exporter, appsReturnedByScan = setOf(first, third))
        appControl = setup.appControl
        appControl.submit(buildScanTask())

        val result = appControl.submit(
            AppExportTask(
                targets = setOf(first.installId, goneId, third.installId),
                savePath = mockk(),
            )
        )

        val exportResult = result.shouldBeInstanceOf<AppExportTask.Result>()
        exportResult.failed shouldBe setOf(goneId)
        exportResult.success.map { it.installId } shouldBe listOf(first.installId, third.installId)
        // The third export starts at 2, i.e. the missing target advanced the counter like any other.
        progressAtSave shouldBe listOf(0L, 2L)
    }

    // ─────────────────────────── performToggle ───────────────────────────

    private fun toggleApp(
        pkgName: String,
        enabled: Boolean,
        canBeToggled: Boolean,
        user: UserHandle2 = systemUserHandle,
    ): AppInfo {
        val pkgId = Pkg.Id(pkgName)
        val pkg = mockk<Installed>(relaxed = true, moreInterfaces = arrayOf(InstallDetails::class)).apply {
            every { id } returns pkgId
            every { packageName } returns pkgName
            every { label } returns null
            every { userHandle } returns user
            every { installId } returns InstallId(pkgId, user)
            every { (this@apply as InstallDetails).isEnabled } returns enabled
        }
        return AppInfo(
            pkg = pkg,
            isActive = null,
            sizes = null,
            usage = null,
            userProfile = null,
            canBeToggled = canBeToggled,
            canBeStopped = false,
            canBeExported = false,
            canBeDeleted = false,
            canBeArchived = false,
            canBeRestored = false,
        )
    }

    /** Answers the post-refresh re-query with whatever [fresh] holds for the requested pkgId. */
    private fun Setup.stubReQuery(vararg fresh: AppInfo) {
        coEvery { appScan.app(any(), any(), any(), any(), any()) } answers {
            fresh.filter { it.id == firstArg<Pkg.Id>() }.toSet()
        }
    }

    /**
     * Resolves a [CaString] without Robolectric by faking the plural lookup that
     * `Context.getQuantityString2` performs, tagging each clause with its resource.
     */
    private fun CaString.resolve(): String {
        val res = mockk<Resources>().apply {
            every { getQuantityString(any(), any(), *anyVararg()) } answers {
                val quantity = secondArg<Int>()
                val name = when (firstArg<Int>()) {
                    eu.darken.sdmse.appcontrol.R.plurals.appcontrol_toggle_result_message_x -> "toggled"
                    eu.darken.sdmse.appcontrol.R.plurals.appcontrol_toggle_result_skipped_x -> "skipped"
                    eu.darken.sdmse.common.R.plurals.result_x_failed -> "failed"
                    else -> "unknown"
                }
                "$quantity $name"
            }
        }
        return get(mockk<Context>().apply { every { resources } returns res })
    }

    @Test
    fun `a target that cannot be toggled is skipped instead of dispatched`() = runTest2 {
        // The reported symptom: a package that is not installed for this user was submitted to
        // changePackageState anyway and then reported as successfully enabled.
        val hidden = toggleApp("eu.thlab.hidden", enabled = true, canBeToggled = false)
        val setup = setupAppControl(appsReturnedByScan = setOf(hidden))
        setup.appControl.submit(buildScanTask())

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(hidden.installId)))

        coVerify(exactly = 0) { setup.componentToggler.changePackageState(any(), any()) }
        result shouldBe AppControlToggleTask.Result(
            enabled = emptySet(),
            disabled = emptySet(),
            failed = emptySet(),
            skipped = setOf(hidden.installId),
        )
    }

    @Test
    fun `an all-skipped task returns without refreshing the package cache`() = runTest2 {
        // Nothing changed, so a full package repository rebuild is wasted work, and a failure
        // inside it would throw away the skip report.
        val hidden = toggleApp("eu.thlab.hidden", enabled = true, canBeToggled = false)
        val setup = setupAppControl(appsReturnedByScan = setOf(hidden))
        setup.appControl.submit(buildScanTask())

        setup.appControl.submit(AppControlToggleTask(targets = setOf(hidden.installId)))

        coVerify(exactly = 0) { setup.appScan.refresh() }
    }

    @Test
    fun `a mixed selection dispatches only the togglable target`() = runTest2 {
        val hidden = toggleApp("eu.thlab.hidden", enabled = true, canBeToggled = false)
        val normal = toggleApp("eu.thlab.normal", enabled = true, canBeToggled = true)
        // Hoisted: reading them inside a verify block would register as calls to verify.
        val hiddenId = hidden.installId
        val normalId = normal.installId
        val setup = setupAppControl(appsReturnedByScan = setOf(hidden, normal))
        setup.appControl.submit(buildScanTask())
        setup.stubReQuery(toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true))

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(hiddenId, normalId)))

        coVerify(exactly = 1) { setup.componentToggler.changePackageState(normalId, false) }
        coVerify(exactly = 0) { setup.componentToggler.changePackageState(hiddenId, any()) }
        result shouldBe AppControlToggleTask.Result(
            enabled = emptySet(),
            disabled = setOf(normalId),
            failed = emptySet(),
            skipped = setOf(hiddenId),
        )
    }

    @Test
    fun `a toggle the refreshed data confirms is reported as toggled`() = runTest2 {
        val target = toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true)
        val setup = setupAppControl(appsReturnedByScan = setOf(target))
        setup.appControl.submit(buildScanTask())
        setup.stubReQuery(toggleApp("eu.thlab.normal", enabled = true, canBeToggled = true))

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(target.installId)))

        result shouldBe AppControlToggleTask.Result(
            enabled = setOf(target.installId),
            disabled = emptySet(),
            failed = emptySet(),
            skipped = emptySet(),
        )
    }

    @Test
    fun `a toggle the refreshed data contradicts is demoted to failed`() = runTest2 {
        // changePackageState not throwing is not evidence that the state actually changed.
        val target = toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true)
        val setup = setupAppControl(appsReturnedByScan = setOf(target))
        setup.appControl.submit(buildScanTask())
        setup.stubReQuery(toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true))

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(target.installId)))

        result shouldBe AppControlToggleTask.Result(
            enabled = emptySet(),
            disabled = emptySet(),
            failed = setOf(target.installId),
            skipped = emptySet(),
        )
    }

    @Test
    fun `a toggle that cannot be verified at all is failed too`() = runTest2 {
        val target = toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true)
        val setup = setupAppControl(appsReturnedByScan = setOf(target))
        setup.appControl.submit(buildScanTask())
        setup.stubReQuery()

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(target.installId)))

        result shouldBe AppControlToggleTask.Result(
            enabled = emptySet(),
            disabled = emptySet(),
            failed = setOf(target.installId),
            skipped = emptySet(),
        )
    }

    @Test
    fun `two users of one package do not produce duplicate entries`() = runTest2 {
        // The re-query is per pkgId and returns every user, so an undeduplicated rebuild lists each
        // user twice - after which the next toggle's single{} lookup throws.
        val otherUserHandle = UserHandle2(handleId = 10)
        val first = toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true)
        val second = toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true, user = otherUserHandle)
        val setup = setupAppControl(useRoot = true, appsReturnedByScan = setOf(first, second))
        setup.appControl.submit(buildScanTask(includeMultiUser = true))
        setup.stubReQuery(
            toggleApp("eu.thlab.normal", enabled = true, canBeToggled = true),
            toggleApp("eu.thlab.normal", enabled = true, canBeToggled = true, user = otherUserHandle),
        )

        setup.appControl.submit(
            AppControlToggleTask(targets = setOf(first.installId, second.installId)),
        )

        val apps = setup.appControl.dataFromState()!!.apps
        apps.map { it.installId } shouldContainExactlyInAnyOrder listOf(first.installId, second.installId)
    }

    @Test
    fun `primaryInfo carries the skipped count`() = runTest2 {
        val hidden = toggleApp("eu.thlab.hidden", enabled = true, canBeToggled = false)
        val setup = setupAppControl(appsReturnedByScan = setOf(hidden))
        setup.appControl.submit(buildScanTask())

        val result = setup.appControl.submit(AppControlToggleTask(targets = setOf(hidden.installId)))

        result.primaryInfo.resolve() shouldBe "1 skipped"
    }

    @Test
    fun `primaryInfo carries every outcome of a mixed selection`() = runTest2 {
        // The list screen's snackbar renders primaryInfo alone, so a skip that only lands in
        // secondaryInfo is invisible there.
        val hidden = toggleApp("eu.thlab.hidden", enabled = true, canBeToggled = false)
        val normal = toggleApp("eu.thlab.normal", enabled = true, canBeToggled = true)
        val stubborn = toggleApp("eu.thlab.stubborn", enabled = true, canBeToggled = true)
        val setup = setupAppControl(appsReturnedByScan = setOf(hidden, normal, stubborn))
        setup.appControl.submit(buildScanTask())
        setup.stubReQuery(
            // Refreshed to the intended post-toggle state, so it stays toggled.
            toggleApp("eu.thlab.normal", enabled = false, canBeToggled = true),
            // Still reporting the pre-toggle state, so the read-back demotes it to failed.
            toggleApp("eu.thlab.stubborn", enabled = true, canBeToggled = true),
        )

        val result = setup.appControl.submit(
            AppControlToggleTask(targets = setOf(hidden.installId, normal.installId, stubborn.installId)),
        )

        result.primaryInfo.resolve() shouldBe "1 toggled, 1 failed, 1 skipped"
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
