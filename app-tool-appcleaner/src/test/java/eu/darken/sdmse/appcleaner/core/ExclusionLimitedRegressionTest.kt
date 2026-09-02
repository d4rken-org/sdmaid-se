package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.scanner.AppScanner
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.NoSettingsDetector
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue
import java.time.Instant
import javax.inject.Provider

/**
 * Regression coverage for how exclusion-limited junks are treated during a clean: they only exist
 * in the results because the device-global ADB cache trim reaches them anyway.
 */
class ExclusionLimitedRegressionTest : BaseTest() {

    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[Job]?.cancel()
    }

    private val testHandle = UserHandle2(handleId = 0)
    private val testUser = UserProfile2(handle = testHandle)

    private fun installId(pkgName: String): InstallId =
        InstallId(pkgId = Pkg.Id(name = pkgName), userHandle = testHandle)

    private fun cacheFor(pkgName: String, itemCount: Int, totalSize: Long) = InaccessibleCache(
        identifier = installId(pkgName),
        isSystemApp = false,
        itemCount = itemCount,
        totalSize = totalSize,
        publicSize = null,
        theoreticalPaths = emptySet(),
    )

    private fun publicCacheMatch(path: LocalPath): ExpendablesFilter.Match = ExpendablesFilter.Match.Deletion(
        identifier = DefaultCachesPublicFilter::class,
        lookup = LocalPathLookup(
            lookedUp = path,
            fileType = FileType.FILE,
            size = 1024L,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
    )

    private fun currentState(complete: Boolean): SetupModule.State.Current = object : SetupModule.State.Current {
        override val type: SetupModule.Type = SetupModule.Type.INVENTORY
        override val isComplete: Boolean = complete
    }

    private fun mockUpgradeRepo(): UpgradeRepo {
        val info = mockk<UpgradeRepo.Info>().apply {
            every { isPro } returns true
            every { isSettled } returns true
            every { error } returns null
        }
        return mockk<UpgradeRepo>(relaxed = true) {
            every { upgradeInfo } returns MutableStateFlow(info)
        }
    }

    /** A filter that reports every target it is handed as deleted, running [onProcess] first. */
    private fun deletingFilterFactory(onProcess: () -> Unit = {}): ExpendablesFilter.Factory {
        val filter = mockk<ExpendablesFilter>(relaxUnitFun = true).apply {
            every { identifier } returns DefaultCachesPublicFilter::class
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coJustRun { initialize() }
            coEvery { process(any(), any()) } answers {
                onProcess()
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

    private fun acsDeleter(succesful: Set<InstallId>): InaccessibleDeleter =
        mockk<InaccessibleDeleter>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery {
                deleteInaccessible(any(), any(), any(), any(), any())
            } returns InaccessibleDeleter.InaccDelResult(
                succesful = succesful,
                failed = emptyMap(),
                freedBytes = emptyMap(),
            )
        }

    /** Mirrors `AppCleanerTest.setupCleaner`, but lets the ADB flow and the deleter be supplied. */
    private fun buildCleaner(
        scanResults: List<AppJunk>,
        adbFlow: Flow<Boolean>,
        deleter: InaccessibleDeleter,
        filterFactories: Set<ExpendablesFilter.Factory> = emptySet(),
    ): AppCleaner {
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
            every { useRoot } returns flowOf(false)
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns adbFlow
        }
        val exclusionManager = mockk<ExclusionManager>().apply {
            every { exclusions } returns flowOf(emptyList())
            coEvery { save(any()) } returns emptyList()
            coJustRun { remove(any()) }
        }
        val inventorySetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(true))
        }
        val usageStatsSetup = mockk<SetupModule>().apply {
            every { state } returns flowOf(currentState(true))
        }
        val scanner = mockk<AppScanner>(relaxUnitFun = true).apply {
            every { progress } returns MutableStateFlow<Progress.Data?>(null)
            every { updateProgress(any()) } just Runs
            coEvery { scan(any()) } returns scanResults
        }
        return AppCleaner(
            appScope = keepAliveScope,
            fileForensics = fileForensics,
            appScannerProvider = Provider { scanner },
            inaccessibleDeleterProvider = Provider { deleter },
            exclusionManager = exclusionManager,
            gatewaySwitch = gatewaySwitch,
            pkgOps = pkgOps,
            usageStatsSetupModule = usageStatsSetup,
            rootManager = rootManager,
            adbManager = adbManager,
            shellOps = shellOps,
            filterFactories = filterFactories,
            appInventorySetupModule = inventorySetup,
            upgradeRepo = mockUpgradeRepo(),
        )
    }

    /** A real [InaccessibleDeleter] sharing [adbFlow] with the cleaner that drives it. */
    private fun buildRealDeleter(
        adbFlow: Flow<Boolean>,
        pkgOps: PkgOps,
        cacheProvider: InaccessibleCacheProvider,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): InaccessibleDeleter {
        val dispatcherProvider = object : DispatcherProvider {
            override val Default: CoroutineDispatcher = dispatcher
            override val Main: CoroutineDispatcher = dispatcher
            override val MainImmediate: CoroutineDispatcher = dispatcher
            override val Unconfined: CoroutineDispatcher = dispatcher
            override val IO: CoroutineDispatcher = dispatcher
        }
        val userManager = mockk<UserManager2>().apply {
            coEvery { currentUser() } returns testUser
        }
        val adbManager = mockk<AdbManager>().apply {
            every { useAdb } returns adbFlow
        }
        val noSettingsDetector = mockk<NoSettingsDetector>().apply {
            coEvery { getUnreachableReason(any()) } returns null
        }
        return InaccessibleDeleter(
            dispatcherProvider = dispatcherProvider,
            userManager = userManager,
            automationManager = mockk<AutomationSubmitter>(relaxed = true),
            adbManager = adbManager,
            pkgOps = pkgOps,
            inaccessibleCacheProvider = cacheProvider,
            rootManager = mockk<RootManager>(relaxed = true),
            settings = mockk<AppCleanerSettings>(relaxed = true),
            generalSettings = mockk<GeneralSettings>().apply {
                every { hasAcsConsent } returns mockDataStoreValue(true)
            },
            automationSetupModule = mockk<SetupModule>(relaxed = true),
            noSettingsDetector = noSettingsDetector,
        )
    }

    // ─────────────────────────── fixture control ───────────────────────────

    /**
     * Positive control for the accessible-deletion fixture: the same wiring, but the junk carrying
     * the accessible matches is NOT exclusion-limited. Its match must be deleted. If this goes red
     * the accessible-deletion pipeline in this file is broken and the sibling test proves nothing.
     */
    @Test
    fun `control - a normal junk's accessible matches are deleted on a whole-tool clean`() = runTest2 {
        val adbFlag = MutableStateFlow(true)
        val path = LocalPath.build("storage", "emulated", "0", "Android", "data", "com.example.plain", "cache", "a.bin")
        val plain = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.plain", label = "plain"),
            expendables = mapOf(DefaultCachesPublicFilter::class to listOf(publicCacheMatch(path))),
            inaccessibleCache = cacheFor("com.example.plain", itemCount = 1, totalSize = 60_000L),
        )
        val cacheProvider = mockk<InaccessibleCacheProvider>().apply {
            coEvery { determineCache(any()) } returns null
        }
        val cleaner = buildCleaner(
            scanResults = listOf(plain),
            adbFlow = adbFlag,
            deleter = buildRealDeleter(adbFlag, mockk<PkgOps>(relaxed = true), cacheProvider),
            filterFactories = setOf(deletingFilterFactory { adbFlag.value = false }),
        )

        cleaner.submit(AppCleanerScanTask())
        val result = cleaner.submit(AppCleanerProcessingTask(useAutomation = false))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        result.affectedPaths shouldContain (path as APath)
    }

    // ─────────────────── accessible deletion vs. the trim ───────────────────

    /**
     * Whether the trim runs is only known inside `InaccessibleDeleter`, minutes after the
     * accessible stage started walking files. The drop is staged by flipping the shared ADB flow
     * while the companion junk's accessible files are processed, so the trim that would have
     * justified touching the limited junk never runs.
     */
    @Test
    fun `an exclusion-limited junk's accessible matches survive a whole-tool clean whose trim never runs`() =
        runTest2 {
            val adbFlag = MutableStateFlow(true)
            val limitedPath =
                LocalPath.build("storage", "emulated", "0", "Android", "data", "com.example.limited", "cache", "blob.bin")
            val limited = previewAppJunk(
                pkg = previewInstalled(pkgName = "com.example.limited", label = "limited"),
                expendables = mapOf(DefaultCachesPublicFilter::class to listOf(publicCacheMatch(limitedPath))),
                inaccessibleCache = cacheFor("com.example.limited", itemCount = 1, totalSize = 60_000L),
                isExclusionLimited = true,
            )
            val normalPath =
                LocalPath.build("storage", "emulated", "0", "Android", "data", "com.example.normal", "cache", "n.bin")
            val normal = previewAppJunk(
                pkg = previewInstalled(pkgName = "com.example.normal", label = "normal"),
                expendables = mapOf(DefaultCachesPublicFilter::class to listOf(publicCacheMatch(normalPath))),
                inaccessibleCache = cacheFor("com.example.normal", itemCount = 1, totalSize = 50_000L),
            )
            val cacheProvider = mockk<InaccessibleCacheProvider>().apply {
                coEvery { determineCache(any()) } returns null
            }
            val deleterPkgOps = mockk<PkgOps>(relaxed = true)
            val cleaner = buildCleaner(
                scanResults = listOf(limited, normal),
                adbFlow = adbFlag,
                deleter = buildRealDeleter(adbFlag, deleterPkgOps, cacheProvider),
                // ADB availability drops while the accessible stage runs: read 1 saw it, read 2 won't.
                filterFactories = setOf(deletingFilterFactory { adbFlag.value = false }),
            )

            cleaner.submit(AppCleanerScanTask())
            val result = cleaner.submit(AppCleanerProcessingTask(useAutomation = false))

            result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
            // Precondition of the scenario: read 2 saw the drop, so the trim -- the only reason the
            // limited junk was in the snapshot at all -- never ran.
            coVerify(exactly = 0) { deleterPkgOps.trimCaches(any(), any(), any()) }
            result.affectedPaths shouldNotContain (limitedPath as APath)
            // Pins the staging: the flag only flips because this junk's files are processed.
            result.affectedPaths shouldContain (normalPath as APath)
        }

    // ─────────────────── trim failures vs. limited junks ───────────────────

    /**
     * `trimCachesWithAdb` records a failure for any candidate whose post-trim cache query comes
     * back null. A limited junk is kept out of the per-app clearing stage, so no clearing attempt
     * was ever made for it and it must not carry a clearing error.
     */
    @Test
    fun `an unconfirmed exclusion-limited junk is not reported as a failed clearing attempt`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val normalPkg = mockk<eu.darken.sdmse.common.pkgs.features.Installed>().apply {
            every { id } returns Pkg.Id("com.example.normal")
            every { userHandle } returns testHandle
            every { installId } returns installId("com.example.normal")
            every { packageName } returns "com.example.normal"
            every { label } returns null
        }
        val limitedPkg = mockk<eu.darken.sdmse.common.pkgs.features.Installed>().apply {
            every { id } returns Pkg.Id("com.example.limited")
            every { userHandle } returns testHandle
            every { installId } returns installId("com.example.limited")
            every { packageName } returns "com.example.limited"
            every { label } returns null
        }
        val normal = AppJunk(
            pkg = normalPkg,
            userProfile = testUser,
            expendables = null,
            inaccessibleCache = cacheFor("com.example.normal", itemCount = 1, totalSize = 50_000L),
        )
        val limited = AppJunk(
            pkg = limitedPkg,
            userProfile = testUser,
            expendables = null,
            inaccessibleCache = cacheFor("com.example.limited", itemCount = 1, totalSize = 60_000L),
            isExclusionLimited = true,
        )

        val pkgOps = mockk<PkgOps>().apply {
            coEvery { trimCaches(any(), any(), any()) } returns Unit
        }
        val cacheProvider = mockk<InaccessibleCacheProvider>().apply {
            // The trim cleared this one and the query can see it.
            coEvery { determineCache(normalPkg) } returns cacheFor("com.example.normal", itemCount = 0, totalSize = 0L)
            // The query for the limited app fails, so the trim cannot confirm it.
            coEvery { determineCache(limitedPkg) } returns null
        }
        val deleter = buildRealDeleter(MutableStateFlow(true), pkgOps, cacheProvider, dispatcher)

        val result = deleter.deleteInaccessible(
            snapshot = AppCleaner.Data(junks = listOf(normal, limited)),
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.failed shouldNotContainKey limited.identifier
    }

    // ─────────────────── affectedCount vs. pruning ───────────────────

    /**
     * `pruneOrphanedExclusionLimited()` drops an exclusion-limited junk that lost its last
     * trim-eligible companion. That is bookkeeping, nothing was deleted for it, so its `itemCount`
     * must not be reported as items cleaned.
     */
    @Test
    fun `affectedCount excludes a pruned exclusion-limited junk's items`() = runTest2 {
        val normal = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.normal", label = "normal"),
            expendables = null,
            inaccessibleCache = cacheFor("com.example.normal", itemCount = 12, totalSize = 50_000L),
        )
        val limited = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.limited", label = "limited"),
            expendables = null,
            inaccessibleCache = cacheFor("com.example.limited", itemCount = 7, totalSize = 60_000L),
            isExclusionLimited = true,
        )
        val cleaner = buildCleaner(
            scanResults = listOf(normal, limited),
            adbFlow = MutableStateFlow(true),
            // The trim confirms the normal junk only. The limited junk is left alone, and the
            // pruning then drops it because nothing trim-eligible remains.
            deleter = acsDeleter(succesful = setOf(installId("com.example.normal"))),
        )

        cleaner.submit(AppCleanerScanTask())
        val result = cleaner.submit(AppCleanerProcessingTask(onlyInaccessible = true))

        result.shouldBeInstanceOf<AppCleanerProcessingTask.Success>()
        // Only the normal junk's 12 items were actually cleaned.
        result.affectedCount shouldBe 12
    }
}
