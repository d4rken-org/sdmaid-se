package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewExpendables
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.setup.IncompleteSetupException
import eu.darken.sdmse.setup.SetupModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import javax.inject.Provider

class AppCleanerTest : BaseTest() {

    // AppCleaner.submit wraps work in `keepResourceHoldersAlive(fileForensics, gatewaySwitch,
    // pkgOps, shellOps)`, each call hits `addChild(sharedResource)` + `sharedResource.get()`.
    // Plain MockK mocks would fail at those calls, so each holder gets a real
    // SharedResource.createKeepAlive backed by a long-lived scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[Job]?.cancel()
    }

    private fun installId(pkgName: String, userId: Int = 0): InstallId =
        InstallId(pkgId = Pkg.Id(name = pkgName), userHandle = UserHandle2(handleId = userId))

    private fun appJunk(
        pkgName: String,
        expendables: Map<kotlin.reflect.KClass<out ExpendablesFilter>, Collection<ExpendablesFilter.Match>>? = previewExpendables(),
    ): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
        expendables = expendables,
        inaccessibleCache = null,
    )

    private class Setup(
        val cleaner: AppCleaner,
        val scanner: AppScanner,
        val exclusionManager: ExclusionManager,
        val inventorySetup: SetupModule,
    )

    private fun currentState(complete: Boolean): SetupModule.State.Current = object : SetupModule.State.Current {
        override val type: SetupModule.Type = SetupModule.Type.INVENTORY
        override val isComplete: Boolean = complete
    }

    private fun setupCleaner(
        inventoryComplete: Boolean = true,
        usageStatsComplete: Boolean = true,
        useRoot: Boolean = false,
        useAdb: Boolean = false,
        scanResults: List<AppJunk> = emptyList(),
        savedExclusions: Collection<Exclusion> = emptyList(),
    ): Setup {
        val fileForensics = mockk<FileForensics>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("ff", keepAliveScope)
        }
        val gatewaySwitch = mockk<GatewaySwitch>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("gw", keepAliveScope)
        }
        val pkgOps = mockk<PkgOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("po", keepAliveScope)
        }
        val shellOps = mockk<ShellOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("so", keepAliveScope)
        }
        val rootManager = mockk<RootManager>().apply {
            every { this@apply.useRoot } returns flowOf(useRoot)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { this@apply.useAdb } returns flowOf(useAdb)
        }
        val exclusionManager = mockk<ExclusionManager>().apply {
            every { exclusions } returns flowOf(emptyList())
            coEvery { save(any()) } returns savedExclusions
            coJustRun { remove(any()) }
        }
        val inventorySetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(inventoryComplete))
        }
        val usageStatsSetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(usageStatsComplete))
        }
        val scanner = mockk<AppScanner>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { scan(any()) } returns scanResults
        }
        val scannerProvider = Provider<AppScanner> { scanner }
        val inaccessibleDeleterProvider = Provider<InaccessibleDeleter> {
            error("Inaccessible deleter is not exercised in these tests")
        }
        val cleaner = AppCleaner(
            appScope = keepAliveScope,
            fileForensics = fileForensics,
            appScannerProvider = scannerProvider,
            inaccessibleDeleterProvider = inaccessibleDeleterProvider,
            exclusionManager = exclusionManager,
            gatewaySwitch = gatewaySwitch,
            pkgOps = pkgOps,
            usageStatsSetupModule = usageStatsSetup,
            rootManager = rootManager,
            adbManager = adbManager,
            shellOps = shellOps,
            filterFactories = emptySet(),
            appInventorySetupModule = inventorySetup,
        )
        return Setup(cleaner, scanner, exclusionManager, inventorySetup)
    }

    private suspend fun AppCleaner.dataFromState(): AppCleaner.Data? =
        state.map { it.data }.first()

    // ─────────────────────────── scan + dispatch tests ───────────────────────────

    @Test
    fun `submit ScanTask forwards empty pkgIdFilter and publishes scanner results`() = runTest2 {
        val a = appJunk("com.example.a")
        val b = appJunk("com.example.b")
        val setup = setupCleaner(scanResults = listOf(a, b))

        val capturedFilter = slot<Collection<Pkg.Id>>()
        coEvery { setup.scanner.scan(capture(capturedFilter)) } returns listOf(a, b)

        val result = setup.cleaner.submit(AppCleanerScanTask())

        result.shouldBeInstanceOf<AppCleanerScanTask.Success>()
        capturedFilter.captured shouldBe emptySet()
        setup.cleaner.dataFromState()!!.junks.toList() shouldContainExactlyInAnyOrder listOf(a, b)
    }

    @Test
    fun `submit ScanTask forwards non-empty pkgIdFilter to the scanner`() = runTest2 {
        // Regression test for what used to be the dead `AppCleanerScanTask.pkgIdFilter` field:
        // performScan now passes it through to AppScanner.scan(pkgFilter=...). The scanner is
        // the one that filters by package id (see AppScanner.kt:149), so we just verify the wire
        // is connected end-to-end — not the scanner-internal filter logic.
        val target = Pkg.Id(name = "com.target")
        val setup = setupCleaner(scanResults = emptyList())
        val capturedFilter = slot<Collection<Pkg.Id>>()
        coEvery { setup.scanner.scan(capture(capturedFilter)) } returns emptyList()

        setup.cleaner.submit(AppCleanerScanTask(pkgIdFilter = setOf(target)))

        capturedFilter.captured.toSet() shouldBe setOf(target)
    }

    @Test
    fun `submit ScanTask throws IncompleteSetupException when inventory setup is incomplete`() = runTest2 {
        val setup = setupCleaner(inventoryComplete = false)
        // submit() catches nothing — the exception propagates out.
        shouldThrow<IncompleteSetupException> {
            setup.cleaner.submit(AppCleanerScanTask())
        }
        // The scanner must never be touched if setup is incomplete.
        coVerify(exactly = 0) { setup.scanner.scan(any()) }
    }

    @Test
    fun `submit ScanTask reports itemCount and recoverableSpace from results`() = runTest2 {
        val a = appJunk("com.example.a")
        val b = appJunk("com.example.b")
        val setup = setupCleaner(scanResults = listOf(a, b))

        val result = setup.cleaner.submit(AppCleanerScanTask()) as AppCleanerScanTask.Success

        // Both helpers come from AppJunk's lazy fields; just check the size/itemCount add up.
        val expectedSize = a.size + b.size
        val expectedCount = a.itemCount + b.itemCount
        // `Success`'s underlying fields are private; we can only verify via toString-free
        // observation of state. Use dataFromState totalSize/totalCount as the observable proxy.
        val data = setup.cleaner.dataFromState()!!
        data.totalSize shouldBe expectedSize
        data.totalCount shouldBe expectedCount
        // Sanity: result is still a Success.
        result.shouldBeInstanceOf<AppCleanerScanTask.Success>()
    }

    // ─────────────────────────── task contract ───────────────────────────

    @Test
    fun `AppCleanerProcessingTask rejects targetContents without targetPkgs`() {
        val anyPath = mockk<APath>()
        val ex = runCatching {
            AppCleanerProcessingTask(
                targetPkgs = null,
                targetContents = setOf(anyPath),
            )
        }.exceptionOrNull()
        // init { require } throws at construction — production callers never produce this shape,
        // but the contract prevents the cross-junk smear bug from being reintroduced.
        ex.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `AppCleanerProcessingTask accepts targetContents paired with targetPkgs`() {
        val anyPath = mockk<APath>()
        val anyPkg = installId("com.example.a")
        // No exception — this is the shape every real caller produces.
        AppCleanerProcessingTask(
            targetPkgs = setOf(anyPkg),
            targetContents = setOf(anyPath),
        )
    }

    @Test
    fun `AppCleanerProcessingTask accepts the delete-all defaults`() {
        // No exception — the dashboard's `delete everything` path constructs this shape.
        AppCleanerProcessingTask()
    }

    @Test
    fun `AppCleanerProcessingTask accepts targetPkgs alone without targetContents`() {
        val anyPkg = installId("com.example.a")
        AppCleanerProcessingTask(targetPkgs = setOf(anyPkg))
    }

    // ─────────────────────────── exclude / undoExclude ───────────────────────────

    @Test
    fun `exclude saves PkgExclusion with APPCLEANER tag and returns ExclusionUndo`() = runTest2 {
        val target = appJunk("com.target")
        val other = appJunk("com.other")
        val setup = setupCleaner(scanResults = listOf(target, other))
        setup.cleaner.submit(AppCleanerScanTask())

        // A distinct synthetic exclusion id from save() proves the undo handle reads from the
        // SAVED set, not from the requested PkgExclusion list. If production accidentally pulled
        // ids off the requested exclusions, this assertion would still flag the regression.
        val savedExclusion = mockk<Exclusion>().apply {
            every { id } returns "saved-1"
        }
        val capturedSave = slot<Set<Exclusion>>()
        coEvery { setup.exclusionManager.save(capture(capturedSave)) } returns listOf(savedExclusion)

        val undo = setup.cleaner.exclude(setOf(target.identifier))

        capturedSave.captured.size shouldBe 1
        val saved = capturedSave.captured.single() as PkgExclusion
        saved.pkgId shouldBe target.identifier.pkgId
        saved.tags shouldBe setOf(Exclusion.Tag.APPCLEANER)

        undo.exclusionIds shouldBe setOf("saved-1")
    }

    @Test
    fun `exclude removes excluded junks from internal data`() = runTest2 {
        val target = appJunk("com.target")
        val keep = appJunk("com.keep")
        val setup = setupCleaner(scanResults = listOf(target, keep))
        setup.cleaner.submit(AppCleanerScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PkgExclusion(
                pkgId = target.identifier.pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            ),
        )

        setup.cleaner.exclude(setOf(target.identifier))

        val data = setup.cleaner.dataFromState()!!
        data.junks.map { it.identifier } shouldContainExactlyInAnyOrder listOf(keep.identifier)
    }

    @Test
    fun `undoExclude calls ExclusionManager remove exactly once with the saved ids`() = runTest2 {
        val target = appJunk("com.target")
        val setup = setupCleaner(scanResults = listOf(target))
        setup.cleaner.submit(AppCleanerScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PkgExclusion(
                pkgId = target.identifier.pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            ),
        )

        val undo = setup.cleaner.exclude(setOf(target.identifier))
        setup.cleaner.undoExclude(undo)

        coVerify(exactly = 1) { setup.exclusionManager.remove(undo.exclusionIds) }
        // No additional remove() calls — guard against double-removal regressions.
        coVerify(exactly = 1) { setup.exclusionManager.remove(any()) }
    }

    @Test
    fun `undoExclude restores previousData when internalData has not moved on`() = runTest2 {
        val target = appJunk("com.target")
        val keep = appJunk("com.keep")
        val setup = setupCleaner(scanResults = listOf(target, keep))
        setup.cleaner.submit(AppCleanerScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PkgExclusion(
                pkgId = target.identifier.pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            ),
        )

        val undo = setup.cleaner.exclude(setOf(target.identifier))
        // Sanity: target is removed before undo.
        setup.cleaner.dataFromState()!!.junks.map { it.identifier } shouldBe listOf(keep.identifier)

        setup.cleaner.undoExclude(undo)

        setup.cleaner.dataFromState()!!.junks.map { it.identifier }
            .toSet() shouldBe setOf(target.identifier, keep.identifier)
    }

    @Test
    fun `undoExclude with stale handle removes ids but skips data restore`() = runTest2 {
        // First scan produces `target`. After exclude + rescan, internalData points at a fresh
        // Data with `replacement`. undoExclude must still remove the exclusion but MUST NOT
        // resurrect `target` over the user's current scan results.
        val target = appJunk("com.target")
        val replacement = appJunk("com.replacement")

        var scanRound = 0
        val setup = setupCleaner(scanResults = listOf(target))
        // Re-stub scanner per round so the second submit returns `replacement` instead.
        coEvery { setup.scanner.scan(any()) } answers {
            scanRound++
            if (scanRound == 1) listOf(target) else listOf(replacement)
        }

        setup.cleaner.submit(AppCleanerScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PkgExclusion(
                pkgId = target.identifier.pkgId,
                tags = setOf(Exclusion.Tag.APPCLEANER),
            ),
        )
        val undo = setup.cleaner.exclude(setOf(target.identifier))

        // Second scan moves internalData on — undo.postExcludeData is no longer the current ref.
        setup.cleaner.submit(AppCleanerScanTask())

        setup.cleaner.undoExclude(undo)

        coVerify(exactly = 1) { setup.exclusionManager.remove(undo.exclusionIds) }
        // The state reflects the post-rescan snapshot, NOT undo.previousData.
        setup.cleaner.dataFromState()!!.junks.map { it.identifier } shouldBe listOf(replacement.identifier)
    }

    @Test
    fun `path-level exclude removes the matched paths from the targeted junk only`() = runTest2 {
        // Path-level `exclude(InstallId, Set<APath>)` filters a single junk's expendables. Other
        // junks must be untouched. The PathExclusion match predicate operates on the per-match
        // path, so picking the lookup path of one of the previewExpendables matches is the
        // canonical case.
        val target = appJunk("com.target")
        val targetMatchPath = target.expendables!!.values.flatten().first().path
        val keep = appJunk("com.keep")
        val setup = setupCleaner(scanResults = listOf(target, keep))
        setup.cleaner.submit(AppCleanerScanTask())

        coEvery { setup.exclusionManager.save(any()) } returns listOf(
            PathExclusion(path = targetMatchPath, tags = setOf(Exclusion.Tag.APPCLEANER)),
        )

        setup.cleaner.exclude(target.identifier, setOf(targetMatchPath))

        val data = setup.cleaner.dataFromState()!!
        // `keep` is unchanged (its match count == previewExpendables count).
        val keepAfter = data.junks.single { it.identifier == keep.identifier }
        keepAfter.expendables?.values?.flatten()?.size shouldBe keep.expendables!!.values.flatten().size
        // `target` lost the excluded match. With previewExpendables = 2 matches, one excluded
        // leaves one. (After `filter { it.second.isNotEmpty() }`, the category survives.)
        val targetAfter = data.junks.single { it.identifier == target.identifier }
        targetAfter.expendables?.values?.flatten()?.map { it.path } shouldBe
            target.expendables!!.values.flatten().drop(1).map { it.path }
    }

    // ─────────────────────────── chained-task dispatch ───────────────────────────

    @Test
    fun `submit OneClickTask chains scan then processing and returns OneClickTask Success`() = runTest2 {
        // OneClickTask invokes performScan + performProcessing internally; we observe by checking
        // the resulting task type. With zero junks the deletion path is a no-op (acsResult=null
        // because includeInaccessible defaults to true but there's nothing to delete).
        val setup = setupCleaner(scanResults = emptyList())
        // performProcessing tries to use the InaccessibleDeleter when includeInaccessible=true.
        // OneClickTask defaults to includeInaccessible=true, but with empty snapshot.junks there
        // are no targets — InaccessibleDeleter is still invoked. Stub the provider to return a
        // relaxed deleter that no-ops.
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery {
                deleteInaccessible(any(), any(), any(), any())
            } returns InaccessibleDeleter.InaccDelResult(succesful = emptySet(), failed = emptyMap())
        }
        // Replace the cleaner's deleter provider by rebuilding from scratch with the stubbed
        // deleter. The cleaner field references the original provider, so we just re-construct
        // a parallel one — simpler than fishing through reflection.
        val rebuilt = rebuildWithDeleter(setup, deleter)

        val result = rebuilt.cleaner.submit(eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask())

        result.shouldBeInstanceOf<eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask.Success>()
        result.affectedSpace shouldBe 0L
        result.affectedPaths shouldBe emptySet()
    }

    @Test
    fun `submit SchedulerTask chains scan then processing and returns SchedulerTask Success`() = runTest2 {
        val setup = setupCleaner(scanResults = emptyList())
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery {
                deleteInaccessible(any(), any(), any(), any())
            } returns InaccessibleDeleter.InaccDelResult(succesful = emptySet(), failed = emptyMap())
        }
        val rebuilt = rebuildWithDeleter(setup, deleter)

        val result = rebuilt.cleaner.submit(
            eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask(
                scheduleId = "test-schedule",
                useAutomation = true,
            ),
        )

        result.shouldBeInstanceOf<eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask.Success>()
        result.affectedSpace shouldBe 0L
        result.affectedPaths shouldBe emptySet()
    }

    /**
     * Re-build a cleaner with the same mocks but a different InaccessibleDeleter. Used by chained-
     * task tests where the OneClick/Scheduler path actually walks the deletion code.
     */
    private fun rebuildWithDeleter(setup: Setup, deleter: InaccessibleDeleter): Setup {
        // Pull the existing scanner+exclusionManager so the rebuilt cleaner shares behaviour. The
        // simpler approach (passing them to setupCleaner) won't work because setupCleaner builds
        // fresh mocks every call — so we just construct a fresh AppCleaner pointing at the same
        // mock collaborators.
        val fileForensics = mockk<FileForensics>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("ff2", keepAliveScope)
        }
        val gatewaySwitch = mockk<GatewaySwitch>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("gw2", keepAliveScope)
        }
        val pkgOps = mockk<PkgOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("po2", keepAliveScope)
        }
        val shellOps = mockk<ShellOps>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("so2", keepAliveScope)
        }
        val rootManager = mockk<RootManager>().apply {
            every { useRoot } returns flowOf(false)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns flowOf(false)
        }
        val inventorySetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(complete = true))
        }
        val usageStatsSetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(complete = true))
        }
        val cleaner = AppCleaner(
            appScope = keepAliveScope,
            fileForensics = fileForensics,
            appScannerProvider = Provider { setup.scanner },
            inaccessibleDeleterProvider = Provider { deleter },
            exclusionManager = setup.exclusionManager,
            gatewaySwitch = gatewaySwitch,
            pkgOps = pkgOps,
            usageStatsSetupModule = usageStatsSetup,
            rootManager = rootManager,
            adbManager = adbManager,
            shellOps = shellOps,
            filterFactories = emptySet(),
            appInventorySetupModule = inventorySetup,
        )
        return Setup(cleaner, setup.scanner, setup.exclusionManager, inventorySetup)
    }

    // ─────────────────────────── delete-path coverage gap ───────────────────────────

    // NOTE: Exercising performProcessing's accessible-delete branch with real Match deletions
    // requires stubbing ExpendablesFilter factories that return real filters whose `process()`
    // succeeds — including coordinated work with the inaccessible deleter for size reconciliation.
    // The complexity outweighs the payoff in this round; the public-API contracts (per-junk path
    // filter, includeInaccessible defaults, targetPkgs/targetContents contract) are protected by
    // the contract test above and by `AppCleanerTaskFactoryTest`. Leaving the delete-path
    // integration to a follow-up that introduces a shared FakeFilter test helper.
}
