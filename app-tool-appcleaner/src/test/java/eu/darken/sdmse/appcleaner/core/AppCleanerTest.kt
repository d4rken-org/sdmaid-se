package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThumbnailsFilter
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerTask
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInaccessibleCache
import eu.darken.sdmse.automation.core.errors.AutomationNoConsentException
import eu.darken.sdmse.automation.core.errors.InvalidSystemStateException
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.errors.ScreenUnavailableException
import eu.darken.sdmse.appcleaner.ui.preview.previewExpendables
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.UpgradeRequiredException
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.main.core.PartialResultException
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
import kotlinx.coroutines.CancellationException
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
import java.time.Instant
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

    private fun deletionMatch(
        path: LocalPath,
        fileType: FileType = FileType.FILE,
    ): ExpendablesFilter.Match = ExpendablesFilter.Match.Deletion(
        identifier = DefaultCachesPublicFilter::class,
        lookup = LocalPathLookup(
            lookedUp = path,
            fileType = fileType,
            size = 1024L,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
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

    private fun mockUpgradeRepo(pro: Boolean): UpgradeRepo {
        val info = mockk<UpgradeRepo.Info>().apply {
            every { isPro } returns pro
            every { isSettled } returns true
            every { error } returns null
        }
        return mockk<UpgradeRepo>(relaxed = true) {
            // Hot and never-completing, like the production repos: the pro gate waits for a pro
            // state to appear, so a finite flow would complete the wait instead of denying.
            every { upgradeInfo } returns MutableStateFlow(info)
        }
    }

    private fun setupCleaner(
        inventoryComplete: Boolean = true,
        usageStatsComplete: Boolean = true,
        useRoot: Boolean = false,
        useAdb: Boolean = false,
        scanResults: List<AppJunk> = emptyList(),
        savedExclusions: Collection<Exclusion> = emptyList(),
        isPro: Boolean = true,
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
            upgradeRepo = mockUpgradeRepo(isPro),
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

    @Test
    fun `discardScanData clears the scan results`() = runTest2 {
        val a = appJunk("com.example.a")
        val setup = setupCleaner(scanResults = listOf(a))
        setup.cleaner.submit(AppCleanerScanTask())
        setup.cleaner.dataFromState()!!.junks.toList() shouldBe listOf(a)

        setup.cleaner.discardScanData()

        setup.cleaner.dataFromState() shouldBe null
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

    @Test
    fun `path-level exclude drops the descendants of an excluded directory`() = runTest2 {
        // exclude() keeps the PathExclusion semantics: the target itself and everything below it
        // goes, so excluding a directory must take its children with it.
        val cacheDir = LocalPath.build("storage", "emulated", "0", "Android", "data", "com.target", "cache")
        val nested = LocalPath.build(
            "storage", "emulated", "0", "Android", "data", "com.target", "cache", "inner", "blob.bin",
        )
        val sibling = LocalPath.build(
            "storage", "emulated", "0", "Android", "data", "com.target", "files", "keep.txt",
        )
        val target = appJunk(
            "com.target",
            expendables = mapOf(
                DefaultCachesPublicFilter::class to listOf(
                    deletionMatch(cacheDir, FileType.DIRECTORY),
                    deletionMatch(nested),
                    deletionMatch(sibling),
                ),
            ),
        )
        val setup = setupCleaner(scanResults = listOf(target))
        setup.cleaner.submit(AppCleanerScanTask())

        setup.cleaner.exclude(target.identifier, setOf(cacheDir))

        val data = setup.cleaner.dataFromState()!!
        val after = data.junks.single { it.identifier == target.identifier }
        after.expendables?.values?.flatten()?.map { it.path } shouldBe listOf(sibling)
    }

    @Test
    fun `path-level exclude keeps the parent of an excluded path`() = runTest2 {
        // The opposite direction: ancestors are NOT pruned here (unlike the scan-time
        // excludeNestedLookups), so a parent entry stays in the junk.
        val cacheDir = LocalPath.build("storage", "emulated", "0", "Android", "data", "com.target", "cache")
        val child = LocalPath.build(
            "storage", "emulated", "0", "Android", "data", "com.target", "cache", "blob.bin",
        )
        val target = appJunk(
            "com.target",
            expendables = mapOf(
                DefaultCachesPublicFilter::class to listOf(
                    deletionMatch(cacheDir, FileType.DIRECTORY),
                    deletionMatch(child),
                ),
            ),
        )
        val setup = setupCleaner(scanResults = listOf(target))
        setup.cleaner.submit(AppCleanerScanTask())

        setup.cleaner.exclude(target.identifier, setOf(child))

        val data = setup.cleaner.dataFromState()!!
        val after = data.junks.single { it.identifier == target.identifier }
        after.expendables?.values?.flatten()?.map { it.path } shouldBe listOf(cacheDir)
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
                deleteInaccessible(any(), any(), any(), any(), any())
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
                deleteInaccessible(any(), any(), any(), any(), any())
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

    // ─────────────────────────── Pro gating ───────────────────────────

    @Test
    fun `submit ProcessingTask is denied with UpgradeRequiredException when not Pro`() = runTest2 {
        val setup = setupCleaner(isPro = false)
        // The gate throws before any processing work. If it didn't fire, performProcessing would
        // reach the inaccessibleDeleterProvider (which error()s here) and throw a different type —
        // so an UpgradeRequiredException proves the gate short-circuited at the boundary.
        shouldThrow<UpgradeRequiredException> {
            setup.cleaner.submit(AppCleanerProcessingTask())
        }
    }

    @Test
    fun `submit ScanTask still succeeds when not Pro`() = runTest2 {
        // Scanning is free; only deletion/processing is Pro-gated.
        val a = appJunk("com.example.a")
        val setup = setupCleaner(isPro = false, scanResults = listOf(a))

        val result = setup.cleaner.submit(AppCleanerScanTask())

        result.shouldBeInstanceOf<AppCleanerScanTask.Success>()
    }

    /**
     * Re-build a cleaner with the same mocks but a different InaccessibleDeleter. Used by chained-
     * task tests where the OneClick/Scheduler path actually walks the deletion code.
     */
    private fun rebuildWithDeleter(
        setup: Setup,
        deleter: InaccessibleDeleter,
        filterFactories: Set<ExpendablesFilter.Factory> = emptySet(),
    ): Setup {
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
            filterFactories = filterFactories,
            appInventorySetupModule = inventorySetup,
            upgradeRepo = mockUpgradeRepo(pro = true),
        )
        return Setup(cleaner, setup.scanner, setup.exclusionManager, inventorySetup)
    }

    // ─────────────────────────── affectedCount alignment ───────────────────────────
    // These exercise the inaccessible/ACS deletion path, where the bug lived: a cache the scan
    // counted as N items is cleared by a single "Clear cache" tap and lands in `affectedPaths` as
    // only its <=2 synthetic dir paths. The displayed count must follow the scan scale, not the
    // path-set size. The accessible per-file delete branch needs real filter factories (see NOTE
    // below) and is left to a follow-up; it already counts per-file, matching the scan.

    private fun inaccJunk(
        pkgName: String,
        itemCount: Int,
        theoreticalPaths: Set<APath>,
    ): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
        expendables = null,
        inaccessibleCache = InaccessibleCache(
            identifier = installId(pkgName),
            isSystemApp = false,
            itemCount = itemCount,
            totalSize = 24L * 1024 * 1024,
            publicSize = null,
            theoreticalPaths = theoreticalPaths,
        ),
    )

    /** A filter that reports every target it is handed as successfully deleted. */
    private fun deletingFilterFactory(): ExpendablesFilter.Factory {
        val filter = mockk<ExpendablesFilter>(relaxUnitFun = true).apply {
            every { identifier } returns DefaultCachesPublicFilter::class
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coJustRun { initialize() }
            coEvery { process(any(), any()) } answers {
                ExpendablesFilter.ProcessResult(
                    success = firstArg<Collection<ExpendablesFilter.Match>>(),
                    failed = emptyList(),
                )
            }
        }
        return mockk<ExpendablesFilter.Factory>().apply {
            coEvery { isEnabled() } returns true
            coEvery { create() } returns filter
        }
    }

    private fun acsDeleter(
        succesful: Set<InstallId>,
        freedBytes: Map<InstallId, Long> = emptyMap(),
        skippedNoConsent: Set<InstallId> = emptySet(),
    ): InaccessibleDeleter =
        mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery {
                deleteInaccessible(any(), any(), any(), any(), any())
            } returns InaccessibleDeleter.InaccDelResult(
                succesful = succesful,
                failed = emptyMap(),
                freedBytes = freedBytes,
                skippedNoConsent = skippedNoConsent,
            )
        }

    @Test
    fun `ProcessingTask reports scan-scale count for ACS-cleared caches, not affectedPaths size`() = runTest2 {
        val paths = setOf<APath>(LocalPath.build("p", "a"), LocalPath.build("p", "b"))
        val junk = inaccJunk("com.example.acs", itemCount = 12, theoreticalPaths = paths)
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = setOf(installId("com.example.acs"))))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        // The cache was scanned as 12 items; clearing it via ACS frees all 12.
        result.affectedCount shouldBe 12
        // …but only the 2 synthetic dir paths are individually recordable.
        result.affectedPaths.size shouldBe 2
    }

    @Test
    fun `ProcessingTask reports the bytes the deleter measured, not the pre-clear size`() = runTest2 {
        // The pre-clear size is only what the cache claimed to hold. An app can report a
        // successful clear and keep every byte, and reporting that as freed space is how a
        // 411MB headline ended up with 212MB of it never leaving the device.
        val junk = inaccJunk("com.example.measured", itemCount = 12, theoreticalPaths = emptySet())
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(
            setup,
            acsDeleter(
                succesful = setOf(installId("com.example.measured")),
                freedBytes = mapOf(installId("com.example.measured") to 1L * 1024 * 1024),
            ),
        )

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        // 1MB observed out of the 24MB the scan saw.
        result.affectedSpace shouldBe 1L * 1024 * 1024
        // Still a full success at scan scale: measuring bytes must not change what was cleared.
        result.affectedCount shouldBe 12
    }

    @Test
    fun `ProcessingTask falls back to the pre-clear size when nothing was measured`() = runTest2 {
        val junk = inaccJunk("com.example.unmeasured", itemCount = 12, theoreticalPaths = emptySet())
        val setup = setupCleaner(scanResults = listOf(junk))
        // No freedBytes entry: the deleter could not measure this one (query failure, cancelled
        // observation, a backend that never got there). The reported figure stays as it was.
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = setOf(installId("com.example.unmeasured"))))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        result.affectedSpace shouldBe 24L * 1024 * 1024
    }

    @Test
    fun `ProcessingTask does not overcount when ACS clearing fails`() = runTest2 {
        val junk = inaccJunk("com.example.fail", itemCount = 12, theoreticalPaths = emptySet())
        val setup = setupCleaner(scanResults = listOf(junk))
        // Empty `succesful` => the cache was not cleared, so nothing was removed from the scan.
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = emptySet()))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        result.affectedCount shouldBe 0
        result.affectedPaths shouldBe emptySet()
    }

    @Test
    fun `a failing ACS stage still records the accessible files it already deleted`() = runTest2 {
        // The accessible deletions hit the disk before the ACS stage runs, but `internalData` is
        // only rewritten after it. When the ACS stage throws, that rewrite must still happen or the
        // dashboard keeps listing files that are gone and advertises space that is already free.
        val junk = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.mixed", label = "com.example.mixed"),
            expendables = previewExpendables(),
            inaccessibleCache = previewInaccessibleCache(pkgName = "com.example.mixed"),
        )
        val boom = InvalidSystemStateException("screen went off mid-run")
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } throws boom
        }
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(setup, deleter, filterFactories = setOf(deletingFilterFactory()))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val expendableSize = previewExpendables().values.flatten().sumOf { it.expectedGain }

        // The task still fails, with the original exception: the salvage must not mask the failure.
        // It rides along on the failure so the run is recorded as a partial success.
        val thrown = shouldThrow<PartialResultException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask())
        }
        thrown.cause shouldBe boom
        val partial = thrown.partialResult.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        partial.affectedSpace shouldBe expendableSize
        // InvalidSystemStateException is not a ScreenUnavailableException, so this is a plain error.
        partial.stoppedEarly shouldBe AppCleanerTask.StopReason.ERROR

        val after = rebuilt.cleaner.state.first().data!!.junks.single()
        // Accessible matches were deleted, so they must be gone from the data the dashboard reads.
        after.expendables.orEmpty().values.flatten().shouldBe(emptyList())
        // The inaccessible cache survives untouched apart from the public-cache bytes we did delete.
        after.inaccessibleCache!!.totalSize shouldBe
            previewInaccessibleCache(pkgName = "com.example.mixed").totalSize - expendableSize
        // No ACS attempt completed, so nothing may be recorded as a permanent ACS failure.
        after.acsError shouldBe null
        after.isUnclearable shouldBe false
    }

    @Test
    fun `a cancel during the ACS stage still records the accessible files it already deleted`() = runTest2 {
        // Cancelling is the likeliest way to interrupt a one-tap run, and it strands the accessible
        // deletions exactly like a hard failure does. The cancellation itself must still propagate.
        val junk = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.mixed", label = "com.example.mixed"),
            expendables = previewExpendables(),
            inaccessibleCache = previewInaccessibleCache(pkgName = "com.example.mixed"),
        )
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery {
                deleteInaccessible(any(), any(), any(), any(), any())
            } throws CancellationException("cancelled mid-ACS")
        }
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(setup, deleter, filterFactories = setOf(deletingFilterFactory()))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        shouldThrow<CancellationException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask())
        }

        val after = rebuilt.cleaner.state.first().data!!.junks.single()
        after.expendables.orEmpty().values.flatten().shouldBe(emptyList())
        after.acsError shouldBe null
    }

    @Test
    fun `caches cleared before a terminal ACS failure are not re-advertised`() = runTest2 {
        // The ACS stage clears one app, then dies. The cleared cache must drop out of the data even
        // though the task fails, or the next dashboard render offers to free bytes that are gone.
        val cleared = installId("com.example.cleared")
        val setup = setupCleaner(
            scanResults = listOf(
                inaccJunk("com.example.cleared", itemCount = 4, theoreticalPaths = emptySet()),
                inaccJunk("com.example.later", itemCount = 4, theoreticalPaths = emptySet()),
            ),
        )
        val boom = InvalidSystemStateException("screen went off after the first app")
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } answers {
                // Report the app that was already cleared, then fail the way the real deleter does.
                // Index, not lastArg(): this is a suspend function, so the trailing JVM argument is
                // the Continuation rather than the callback.
                arg<(InaccessibleDeleter.InaccDelResult) -> Unit>(4)
                    .invoke(InaccessibleDeleter.InaccDelResult(succesful = setOf(cleared), failed = emptyMap()))
                throw boom
            }
        }
        val rebuilt = rebuildWithDeleter(setup, deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val thrown = shouldThrow<PartialResultException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask())
        }
        thrown.cause shouldBe boom
        val partial = thrown.partialResult.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        // What the deleter cleared before dying is what the run gets credited with.
        partial.affectedSpace shouldBe 24L * 1024 * 1024
        partial.affectedCount shouldBe 4
        partial.stoppedEarly shouldBe AppCleanerTask.StopReason.ERROR

        // The cleared junk is gone entirely; only the app the run never reached is still listed.
        val remaining = rebuilt.cleaner.state.first().data!!.junks
        remaining.map { it.identifier } shouldBe listOf(installId("com.example.later"))
    }

    /** A cleaner whose accessible stage deletes everything and whose ACS stage dies with [failure]. */
    private fun salvageSetup(failure: Exception): Setup {
        val junk = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.mixed", label = "com.example.mixed"),
            expendables = previewExpendables(),
            inaccessibleCache = previewInaccessibleCache(pkgName = "com.example.mixed"),
        )
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } throws failure
        }
        return rebuildWithDeleter(
            setupCleaner(scanResults = listOf(junk)),
            deleter,
            filterFactories = setOf(deletingFilterFactory()),
        )
    }

    @Test
    fun `a locked screen aborting the ACS stage is reported as such`() = runTest2 {
        val boom = ScreenUnavailableException("screen is off")
        val rebuilt = salvageSetup(boom)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val thrown = shouldThrow<PartialResultException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask())
        }

        thrown.cause shouldBe boom
        val partial = thrown.partialResult.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        partial.stoppedEarly shouldBe AppCleanerTask.StopReason.SCREEN_UNAVAILABLE
    }

    @Test
    fun `an ACS failure with nothing salvaged stays a plain failure`() = runTest2 {
        // No accessible deletions and no ACS successes: there is no partial result to advertise, so
        // wrapping the failure would only claim a partial success that freed nothing.
        val junk = inaccJunk("com.example.nothing", itemCount = 4, theoreticalPaths = emptySet())
        val boom = InvalidSystemStateException("died before anything was cleared")
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } throws boom
        }
        val rebuilt = rebuildWithDeleter(setupCleaner(scanResults = listOf(junk)), deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        shouldThrow<InvalidSystemStateException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask())
        } shouldBe boom
    }

    @Test
    fun `a one-click run salvages its partial result as a one-click success`() = runTest2 {
        // The task manager records the result against the submitted task, so the salvaged result
        // has to be the type that task's own callers expect.
        val boom = InvalidSystemStateException("screen went off mid-run")
        val rebuilt = salvageSetup(boom)
        val expendableSize = previewExpendables().values.flatten().sumOf { it.expectedGain }

        val thrown = shouldThrow<PartialResultException> {
            rebuilt.cleaner.submit(AppCleanerOneClickTask())
        }

        thrown.cause shouldBe boom
        val partial = thrown.partialResult.shouldBeInstanceOf<AppCleanerOneClickTask.Success>()
        partial.affectedSpace shouldBe expendableSize
        partial.stoppedEarly shouldBe AppCleanerTask.StopReason.ERROR
    }

    @Test
    fun `a scheduled run salvages its partial result as a scheduler success`() = runTest2 {
        val boom = ScreenUnavailableException("screen is off")
        val rebuilt = salvageSetup(boom)
        val expendableSize = previewExpendables().values.flatten().sumOf { it.expectedGain }

        val thrown = shouldThrow<PartialResultException> {
            rebuilt.cleaner.submit(AppCleanerSchedulerTask(scheduleId = "schedule-1", useAutomation = true))
        }

        thrown.cause shouldBe boom
        val partial = thrown.partialResult.shouldBeInstanceOf<AppCleanerSchedulerTask.Success>()
        partial.affectedSpace shouldBe expendableSize
        partial.stoppedEarly shouldBe AppCleanerTask.StopReason.SCREEN_UNAVAILABLE
    }

    @Test
    fun `a junk untouched by a later targeted run keeps its acsError`() = runTest2 {
        val stuck = installId("com.example.stuck")
        val other = installId("com.example.other")
        val setup = setupCleaner(
            scanResults = listOf(
                inaccJunk("com.example.stuck", itemCount = 2, theoreticalPaths = emptySet()),
                inaccJunk("com.example.other", itemCount = 2, theoreticalPaths = emptySet()),
            ),
        )
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } returnsMany listOf(
                // First run: "stuck" fails permanently, "other" is left over.
                InaccessibleDeleter.InaccDelResult(
                    succesful = emptySet(),
                    failed = mapOf(stuck to NoSettingsWindowException("no settings window")),
                ),
                // Second run targets only "other" and succeeds; "stuck" is not attempted.
                InaccessibleDeleter.InaccDelResult(succesful = setOf(other), failed = emptyMap()),
            )
        }
        val rebuilt = rebuildWithDeleter(setup, deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))
        rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true, targetPkgs = setOf(other)))

        // The untargeted run must not wipe what the first attempt learned about "stuck".
        val junk = rebuilt.cleaner.state.first().data!!.junks.single()
        junk.identifier shouldBe stuck
        junk.acsError.shouldBeInstanceOf<NoSettingsWindowException>()
        junk.isUnclearable shouldBe true
    }

    @Test
    fun `a new attempt's failure replaces the stored acsError`() = runTest2 {
        val stuck = installId("com.example.stuck")
        val setup = setupCleaner(
            scanResults = listOf(inaccJunk("com.example.stuck", itemCount = 2, theoreticalPaths = emptySet())),
        )
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } returnsMany listOf(
                InaccessibleDeleter.InaccDelResult(
                    succesful = emptySet(),
                    failed = mapOf(stuck to NoSettingsWindowException("no settings window")),
                ),
                InaccessibleDeleter.InaccDelResult(
                    succesful = emptySet(),
                    failed = mapOf(stuck to InvalidSystemStateException("screen was off")),
                ),
            )
        }
        val rebuilt = rebuildWithDeleter(setup, deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))
        rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        // The latest attempt speaks for the junk: a transient failure lifts the unclearable mark.
        val junk = rebuilt.cleaner.state.first().data!!.junks.single()
        junk.acsError.shouldBeInstanceOf<InvalidSystemStateException>()
        junk.isUnclearable shouldBe false
    }

    @Test
    fun `a run without inaccessible deletion preserves the stored acsError`() = runTest2 {
        val stuck = installId("com.example.stuck")
        val setup = setupCleaner(
            scanResults = listOf(inaccJunk("com.example.stuck", itemCount = 2, theoreticalPaths = emptySet())),
        )
        val deleter = mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { deleteInaccessible(any(), any(), any(), any(), any()) } returns InaccessibleDeleter.InaccDelResult(
                succesful = emptySet(),
                failed = mapOf(stuck to NoSettingsWindowException("no settings window")),
            )
        }
        val rebuilt = rebuildWithDeleter(setup, deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))
        rebuilt.cleaner.submit(AppCleanerProcessingTask(includeInaccessible = false))

        val junk = rebuilt.cleaner.state.first().data!!.junks.single()
        junk.acsError.shouldBeInstanceOf<NoSettingsWindowException>()
        junk.isUnclearable shouldBe true
    }

    @Test
    fun `ProcessingTask with includeInaccessible false leaves the cache uncounted`() = runTest2 {
        val junk = inaccJunk("com.example.skip", itemCount = 12, theoreticalPaths = emptySet())
        // includeInaccessible=false never invokes the deleter, so setupCleaner's erroring provider is fine.
        val setup = setupCleaner(scanResults = listOf(junk))

        setup.cleaner.submit(AppCleanerScanTask())
        val result = setup.cleaner.submit(AppCleanerProcessingTask(includeInaccessible = false))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        result.affectedCount shouldBe 0
    }

    @Test
    fun `OneClickTask propagates the scan-scale affectedCount`() = runTest2 {
        val paths = setOf<APath>(LocalPath.build("p", "a"), LocalPath.build("p", "b"))
        val junk = inaccJunk("com.example.oneclick", itemCount = 12, theoreticalPaths = paths)
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = setOf(installId("com.example.oneclick"))))

        val result = rebuilt.cleaner.submit(eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask())

        result.shouldBeInstanceOf<eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask.Success>()
        result.affectedCount shouldBe 12
        result.affectedPaths.size shouldBe 2
    }

    @Test
    fun `SchedulerTask propagates the scan-scale affectedCount`() = runTest2 {
        val paths = setOf<APath>(LocalPath.build("p", "a"), LocalPath.build("p", "b"))
        val junk = inaccJunk("com.example.scheduler", itemCount = 12, theoreticalPaths = paths)
        val setup = setupCleaner(scanResults = listOf(junk))
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = setOf(installId("com.example.scheduler"))))

        val result = rebuilt.cleaner.submit(
            eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask(
                scheduleId = "test-schedule",
                useAutomation = true,
            ),
        )

        result.shouldBeInstanceOf<eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask.Success>()
        result.affectedCount shouldBe 12
        result.affectedPaths.size shouldBe 2
    }

    // ─────────────────────────── exclusion-limited junks ───────────────────────────

    private fun pkgExclusionFor(junk: AppJunk) = listOf(
        PkgExclusion(
            pkgId = junk.identifier.pkgId,
            tags = setOf(Exclusion.Tag.APPCLEANER),
        ),
    )

    private fun thumbnailMatch(path: LocalPath): ExpendablesFilter.Match = ExpendablesFilter.Match.Deletion(
        identifier = ThumbnailsFilter::class,
        lookup = LocalPathLookup(
            lookedUp = path,
            fileType = FileType.FILE,
            size = 1024L,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
    )

    private fun inaccessibleOnlyJunk(pkgName: String, isExclusionLimited: Boolean = false) = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
        expendables = null,
        inaccessibleCache = previewInaccessibleCache(pkgName = pkgName),
        isExclusionLimited = isExclusionLimited,
    )

    @Test
    fun `exclude narrows a junk to the trim blast radius while the trim can still run`() = runTest2 {
        val target = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.target", label = "com.target"),
            expendables = previewExpendables() + mapOf(
                ThumbnailsFilter::class to listOf(thumbnailMatch(LocalPath.build("thumbs", "a.png"))),
            ),
            inaccessibleCache = null,
        )
        val keep = inaccessibleOnlyJunk("com.keep")
        val setup = setupCleaner(useAdb = true, scanResults = listOf(target, keep))
        setup.cleaner.submit(AppCleanerScanTask())
        coEvery { setup.exclusionManager.save(any()) } returns pkgExclusionFor(target)

        setup.cleaner.exclude(setOf(target.identifier))

        val junks = setup.cleaner.dataFromState()!!.junks.associateBy { it.identifier }
        junks.keys.toList() shouldContainExactlyInAnyOrder listOf(target.identifier, keep.identifier)
        val narrowed = junks.getValue(target.identifier)
        narrowed.isExclusionLimited shouldBe true
        narrowed.expendables!!.keys shouldBe setOf(DefaultCachesPublicFilter::class)
    }

    @Test
    fun `exclude removes the junk outright when ADB is unusable`() = runTest2 {
        val target = inaccessibleOnlyJunk("com.target")
        val keep = inaccessibleOnlyJunk("com.keep")
        val setup = setupCleaner(useAdb = false, scanResults = listOf(target, keep))
        setup.cleaner.submit(AppCleanerScanTask())
        coEvery { setup.exclusionManager.save(any()) } returns pkgExclusionFor(target)

        setup.cleaner.exclude(setOf(target.identifier))

        setup.cleaner.dataFromState()!!.junks.map { it.identifier } shouldBe listOf(keep.identifier)
    }

    @Test
    fun `a snapshot whose last trim-eligible junk is gone drops its exclusion-limited entries`() = runTest2 {
        val limited = inaccessibleOnlyJunk("com.limited", isExclusionLimited = true)
        val target = inaccessibleOnlyJunk("com.target")
        val setup = setupCleaner(useAdb = true, scanResults = listOf(limited, target))
        setup.cleaner.submit(AppCleanerScanTask())
        coEvery { setup.exclusionManager.save(any()) } returns pkgExclusionFor(target)

        // Excluding the only junk that would make the trim run leaves nothing to warn about.
        setup.cleaner.exclude(setOf(target.identifier))

        setup.cleaner.dataFromState()!!.junks shouldBe emptyList()
    }

    @Test
    fun `a targeted clean that consumes the last trim-eligible junk drops the limited entries`() = runTest2 {
        val normal = inaccJunk("com.normal", itemCount = 3, theoreticalPaths = emptySet())
        val limited = inaccessibleOnlyJunk("com.limited", isExclusionLimited = true)
        val setup = setupCleaner(scanResults = listOf(normal, limited))
        val rebuilt = rebuildWithDeleter(setup, acsDeleter(succesful = setOf(installId("com.normal"))))

        rebuilt.cleaner.submit(AppCleanerScanTask())
        rebuilt.cleaner.submit(
            AppCleanerProcessingTask(
                targetPkgs = setOf(normal.identifier),
                onlyInaccessible = true,
            ),
        )

        rebuilt.cleaner.state.first().data!!.junks shouldBe emptyList()
    }

    @Test
    fun `a whole-tool clean without a trim leaves an exclusion-limited junk alone`() = runTest2 {
        val limitedPath = LocalPath.build("limited", "cache", "a.bin")
        val limited = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.limited", label = "com.limited"),
            expendables = mapOf(DefaultCachesPublicFilter::class to listOf(deletionMatch(limitedPath))),
            inaccessibleCache = null,
            isExclusionLimited = true,
        )
        val normal = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.normal", label = "com.normal"),
            expendables = previewExpendables(),
            inaccessibleCache = previewInaccessibleCache(pkgName = "com.normal"),
        )
        val setup = setupCleaner(scanResults = listOf(limited, normal))
        // rebuildWithDeleter wires an AdbManager that reports ADB as unusable, so no trim will run.
        val rebuilt = rebuildWithDeleter(
            setup,
            acsDeleter(succesful = emptySet()),
            filterFactories = setOf(deletingFilterFactory()),
        )

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask())

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        val after = rebuilt.cleaner.state.first().data!!.junks.associateBy { it.identifier }
        after.getValue(limited.identifier).expendables!!.values.flatten().map { it.path } shouldBe listOf(limitedPath)
        after.getValue(normal.identifier).expendables.orEmpty().values.flatten() shouldBe emptyList()
        result.affectedPaths shouldBe normal.expendables!!.values.flatten().map { it.path }.toSet()
    }

    // ─────────────────────────── delete-path coverage gap ───────────────────────────

    // NOTE: Exercising performProcessing's accessible-delete branch with real Match deletions
    // requires stubbing ExpendablesFilter factories that return real filters whose `process()`
    // succeeds — including coordinated work with the inaccessible deleter for size reconciliation.
    // The complexity outweighs the payoff in this round; the public-API contracts (per-junk path
    // filter, includeInaccessible defaults, targetPkgs/targetContents contract) are protected by
    // the contract test above and by `AppCleanerTaskFactoryTest`. Leaving the delete-path
    // integration to a follow-up that introduces a shared FakeFilter test helper.

    @Test
    fun `caches skipped for lack of ACS consent are reported on an otherwise successful run`() = runTest2 {
        // The accessible files really were deleted, so the run is a success. It just did not get
        // through the inaccessible caches, and saying so is the only way the user learns why.
        val junk = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.mixed", label = "com.example.mixed"),
            expendables = previewExpendables(),
            inaccessibleCache = previewInaccessibleCache(pkgName = "com.example.mixed"),
        )
        val deleter = acsDeleter(
            succesful = emptySet(),
            skippedNoConsent = setOf(installId("com.example.mixed")),
        )
        val rebuilt = rebuildWithDeleter(
            setupCleaner(scanResults = listOf(junk)),
            deleter,
            filterFactories = setOf(deletingFilterFactory()),
        )

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val result = rebuilt.cleaner.submit(AppCleanerProcessingTask())

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        result.stoppedEarly shouldBe AppCleanerTask.StopReason.AUTOMATION_NO_CONSENT
        result.skippedCount shouldBe 1
        result.affectedSpace shouldBe previewExpendables().values.flatten().sumOf { it.expectedGain }
    }

    @Test
    fun `a consent skip that salvaged nothing still fails with the consent error`() = runTest2 {
        // A targeted inaccessible delete has no other mechanism, so "Success, 0 items" would hide
        // the one prompt that offers the user a way to grant consent.
        val junk = inaccJunk("com.example.nothing", itemCount = 4, theoreticalPaths = emptySet())
        val deleter = acsDeleter(
            succesful = emptySet(),
            skippedNoConsent = setOf(installId("com.example.nothing")),
        )
        val rebuilt = rebuildWithDeleter(setupCleaner(scanResults = listOf(junk)), deleter)

        rebuilt.cleaner.submit(AppCleanerScanTask())
        val thrown = shouldThrow<InaccessibleDeletionException> {
            rebuilt.cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))
        }
        thrown.cause.shouldBeInstanceOf<AutomationNoConsentException>()
    }
}
