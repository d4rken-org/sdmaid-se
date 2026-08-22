package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.automation.core.ForceStopAutomationTask
import eu.darken.sdmse.automation.core.errors.AutomationCompatibilityException
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.automation.core.errors.UserCancelledAutomationException
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.pkgs.NoSettingsDetector
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallDetails
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue
import java.util.Collections

class InaccessibleDeleterTest : BaseTest() {

    private val testHandle = UserHandle2(handleId = 0)
    private val testUser = UserProfile2(handle = testHandle)

    @MockK lateinit var userManager: UserManager2
    @MockK lateinit var automationManager: AutomationSubmitter
    @MockK lateinit var adbManager: AdbManager
    @MockK lateinit var pkgOps: PkgOps
    @MockK lateinit var inaccessibleCacheProvider: InaccessibleCacheProvider
    @MockK lateinit var rootManager: RootManager
    @MockK lateinit var settings: AppCleanerSettings
    @MockK(name = "automation") lateinit var automationSetupModule: SetupModule
    @MockK lateinit var noSettingsDetector: NoSettingsDetector

    private lateinit var deleter: InaccessibleDeleter

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        val testDispatcher = UnconfinedTestDispatcher()
        val dispatcherProvider = object : DispatcherProvider {
            override val Default: CoroutineDispatcher = testDispatcher
            override val Main: CoroutineDispatcher = testDispatcher
            override val MainImmediate: CoroutineDispatcher = testDispatcher
            override val Unconfined: CoroutineDispatcher = testDispatcher
            override val IO: CoroutineDispatcher = testDispatcher
        }

        coEvery { userManager.currentUser() } returns testUser
        every { adbManager.useAdb } returns flowOf(false)
        // Default: no package is flagged as having no settings. Individual tests override as needed.
        coEvery { noSettingsDetector.getUnreachableReason(any()) } returns null

        deleter = InaccessibleDeleter(
            dispatcherProvider = dispatcherProvider,
            userManager = userManager,
            automationManager = automationManager,
            adbManager = adbManager,
            pkgOps = pkgOps,
            inaccessibleCacheProvider = inaccessibleCacheProvider,
            rootManager = rootManager,
            settings = settings,
            automationSetupModule = automationSetupModule,
            noSettingsDetector = noSettingsDetector,
        )
    }

    private var logCollector: Logging.Logger? = null

    @AfterEach
    fun removeLogCollector() {
        logCollector?.let { Logging.remove(it) }
        logCollector = null
    }

    /**
     * Captures log output for tests that assert a specific diagnostic was emitted. A number that
     * could not be measured has to be recognisable as such in a debug log, so the line saying so
     * is part of the contract, not decoration.
     */
    private fun collectLogs(): List<String> {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val logger = object : Logging.Logger {
            override fun log(
                priority: Logging.Priority,
                tag: String,
                message: String,
                metaData: Map<String, Any>?,
            ) {
                lines.add("${priority.shortLabel}/$tag: $message")
            }
        }
        Logging.install(logger)
        logCollector = logger
        return lines
    }

    private fun createAppJunk(
        pkgName: String,
        cacheSize: Long,
        isSystemApp: Boolean = false,
    ): AppJunk {
        val installId = InstallId(Pkg.Id(pkgName), testHandle)
        // Pkg.isSystemApp is an extension: `(this is InstallDetails) && this.isSystemApp`.
        // A plain Installed mock isn't an InstallDetails, so it already reads as a user app.
        val pkg = when {
            isSystemApp -> mockk<Installed>(moreInterfaces = arrayOf(InstallDetails::class))
            else -> mockk<Installed>()
        }
        every { pkg.id } returns Pkg.Id(pkgName)
        every { pkg.userHandle } returns testHandle
        every { pkg.installId } returns installId
        every { pkg.packageName } returns pkgName
        every { pkg.label } returns null
        if (isSystemApp) every { (pkg as InstallDetails).isSystemApp } returns true
        val cache = InaccessibleCache(
            identifier = installId,
            isSystemApp = isSystemApp,
            itemCount = 1,
            totalSize = cacheSize,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )
        return AppJunk(
            pkg = pkg,
            userProfile = testUser,
            expendables = null,
            inaccessibleCache = cache,
        )
    }

    private fun createSnapshot(vararg junks: AppJunk) = AppCleaner.Data(junks = junks.toList())

    @Test
    fun `zero cache target is excluded from automation and marked successful`() = runTest {
        val junk = createAppJunk("com.example.app", cacheSize = 50000)
        val snapshot = createSnapshot(junk)

        // After scan, cache has become zero
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns InaccessibleCache(
            identifier = junk.identifier,
            isSystemApp = false,
            itemCount = 0,
            totalSize = 0L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
        result.failed.shouldBeEmpty()
    }

    @Test
    fun `non-zero cache target stays in automation queue`() = runTest {
        val junk = createAppJunk("com.example.app", cacheSize = 50000)
        val snapshot = createSnapshot(junk)

        // Cache is still non-zero
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns InaccessibleCache(
            identifier = junk.identifier,
            isSystemApp = false,
            itemCount = 1,
            totalSize = 30000,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        // Not marked as success (useAutomation=false so no automation ran either)
        result.succesful shouldNotContain junk.identifier
    }

    @Test
    fun `system app cache is polled, a stale size does not send it to automation`() = runTest {
        // Regression: StorageStatsManager keeps reporting the pre-trim size for a moment after
        // trimCaches. A single immediate read made an already-cleared system app look untouched,
        // which handed it to automation, where the "clear cache" button is disabled and every
        // attempt burned the full step timeout.
        val junk = createAppJunk("com.example.system", cacheSize = 581632, isSystemApp = true)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        // Stubbed so that a regression fails on the coVerify below rather than on an unstubbed call.
        coEvery { automationManager.submit(match { it is ClearCacheTask }) } returns ClearCacheTask.Result(
            successful = emptySet(),
            failed = emptyMap(),
        )

        // The first two reads are stale, the cache is actually already empty.
        var reads = 0
        coEvery { inaccessibleCacheProvider.determineCache(any()) } answers {
            reads++
            InaccessibleCache(
                identifier = junk.identifier,
                isSystemApp = true,
                itemCount = if (reads <= 2) 1 else 0,
                totalSize = if (reads <= 2) 581632L else 0L,
                publicSize = 0L,
                theoreticalPaths = emptySet(),
            )
        }

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
        result.failed.shouldBeEmpty()
        coVerify(exactly = 0) { automationManager.submit(any()) }
    }

    @Test
    fun `targets that never settle do not starve the rest of the batch`() = runTest {
        // A per-target coroutine fan-out (flatMapMerge defaults to concurrency 16) would let the
        // first 16 hold every slot until the deadline, so the remaining targets would never be
        // sampled at all. Rounds must sample every pending target every time.
        val stuck = (1..16).map { createAppJunk("com.example.stuck$it", cacheSize = 581632, isSystemApp = true) }
        val settling = (1..24).map { createAppJunk("com.example.settling$it", cacheSize = 581632, isSystemApp = true) }
        val snapshot = createSnapshot(*(stuck + settling).toTypedArray())

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit

        val reads = mutableMapOf<String, Int>()
        coEvery { inaccessibleCacheProvider.determineCache(any()) } answers {
            val pkg = firstArg<Installed>()
            val seen = reads.merge(pkg.packageName, 1, Int::plus)!!
            // "stuck" never changes. "settling" drops to a smaller but NON-zero size after a few
            // reads; non-zero matters because the caller's zero-only guard must not be what
            // rescues them, otherwise this wouldn't be testing the polling at all.
            val settled = pkg.packageName.startsWith("com.example.settling") && seen > 2
            InaccessibleCache(
                identifier = InstallId(pkg.id, testHandle),
                isSystemApp = true,
                itemCount = 1,
                totalSize = if (settled) 1024L else 581632L,
                publicSize = 0L,
                theoreticalPaths = emptySet(),
            )
        }

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        settling.forEach { result.succesful shouldContain it.identifier }
        stuck.forEach { result.succesful shouldNotContain it.identifier }
    }

    @Test
    fun `system app whose cache decreased is successful`() = runTest {
        val junk = createAppJunk("com.example.system", cacheSize = 581632, isSystemApp = true)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns InaccessibleCache(
            identifier = junk.identifier,
            isSystemApp = true,
            itemCount = 1,
            totalSize = 1024L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
    }

    @Test
    fun `system app with unchanged non-zero cache is not successful`() = runTest {
        val junk = createAppJunk("com.example.system", cacheSize = 581632, isSystemApp = true)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        // Still the same non-zero size: trimCaches genuinely did not clear this one.
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns InaccessibleCache(
            identifier = junk.identifier,
            isSystemApp = true,
            itemCount = 1,
            totalSize = 581632,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldNotContain junk.identifier
    }

    @Test
    fun `system app with a failing cache query is not successful`() = runTest {
        val junk = createAppJunk("com.example.system", cacheSize = 581632, isSystemApp = true)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        // A failed query must not be mistaken for an empty cache.
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns null

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldNotContain junk.identifier
    }

    @Test
    fun `ADB runs before reachability is considered, so unreachable targets still get cleaned`() = runTest {
        val junk = createAppJunk("com.example.disabled", cacheSize = 50000)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        // ADB cleared it: the re-query reports a smaller cache than the scan did
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns InaccessibleCache(
            identifier = junk.identifier,
            isSystemApp = false,
            itemCount = 0,
            totalSize = 0L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
        // The ACS-only reachability check must never gate the ADB backend
        coVerify(exactly = 0) { noSettingsDetector.getUnreachableReason(any()) }
        coVerify(exactly = 0) { automationManager.submit(any()) }
    }

    @Test
    fun `null cache query does not exclude target`() = runTest {
        val junk = createAppJunk("com.example.app", cacheSize = 50000)
        val snapshot = createSnapshot(junk)

        // determineCache returns null (query failed)
        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns null

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        // Not marked as success — would proceed to automation if enabled
        result.succesful shouldNotContain junk.identifier
    }

    @Test
    fun `bookkeeping cleanup removes failed entry when later successful`() = runTest {
        val junk1 = createAppJunk("com.example.cleared", cacheSize = 50000)
        val junk2 = createAppJunk("com.example.failed", cacheSize = 80000)
        val snapshot = createSnapshot(junk1, junk2)

        // Enable ADB path so we can create a "failed" entry from trimCaches
        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any()) } throws RuntimeException("trimCaches failed")

        // But pre-automation revalidation finds junk1's cache is now zero
        coEvery { inaccessibleCacheProvider.determineCache(junk1.pkg) } returns InaccessibleCache(
            identifier = junk1.identifier,
            isSystemApp = false,
            itemCount = 0,
            totalSize = 0L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )
        coEvery { inaccessibleCacheProvider.determineCache(junk2.pkg) } returns InaccessibleCache(
            identifier = junk2.identifier,
            isSystemApp = false,
            itemCount = 1,
            totalSize = 80000,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        // junk1 was marked failed by trimCaches, but revalidation found it's now zero
        // Bookkeeping cleanup should remove it from failed
        result.succesful shouldContain junk1.identifier
        result.failed shouldNotContainKey junk1.identifier

        // junk2 stays as failed (trimCaches failed, cache still non-zero)
        result.failed shouldContainKey junk2.identifier
    }

    @Test
    fun `multiple targets - only zero cache targets are excluded`() = runTest {
        val junkZero = createAppJunk("com.example.empty", cacheSize = 100000)
        val junkNonZero = createAppJunk("com.example.full", cacheSize = 200000)
        val junkNull = createAppJunk("com.example.unknown", cacheSize = 50000)
        val snapshot = createSnapshot(junkZero, junkNonZero, junkNull)

        coEvery { inaccessibleCacheProvider.determineCache(junkZero.pkg) } returns InaccessibleCache(
            identifier = junkZero.identifier,
            isSystemApp = false,
            itemCount = 0,
            totalSize = 0L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )
        coEvery { inaccessibleCacheProvider.determineCache(junkNonZero.pkg) } returns InaccessibleCache(
            identifier = junkNonZero.identifier,
            isSystemApp = false,
            itemCount = 1,
            totalSize = 150000,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )
        coEvery { inaccessibleCacheProvider.determineCache(junkNull.pkg) } returns null

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldContain junkZero.identifier
        result.succesful shouldNotContain junkNonZero.identifier
        result.succesful shouldNotContain junkNull.identifier
        result.succesful.size shouldBe 1
    }

    private fun nonZeroCache(junk: AppJunk) = InaccessibleCache(
        identifier = junk.identifier,
        isSystemApp = false,
        itemCount = 1,
        totalSize = junk.inaccessibleCache!!.totalSize,
        publicSize = 0L,
        theoreticalPaths = emptySet(),
    )

    @Test
    fun `no-settings target is pre-flight failed when useAutomation=true`() = runTest {
        val junk = createAppJunk("com.apex.module", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns nonZeroCache(junk)
        coEvery { noSettingsDetector.getUnreachableReason(junk.pkg) } returns NoSettingsDetector.Reason.NO_SETTINGS_PAGE
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.failed shouldContainKey junk.identifier
        result.failed[junk.identifier].shouldBeInstanceOf<NoSettingsWindowException>()
        result.succesful shouldNotContain junk.identifier
        // ACS must NOT be called — there's nothing to submit
        coVerify(exactly = 0) { automationManager.submit(any()) }
    }

    @Test
    fun `no-settings target is not pre-flight failed when useAutomation=false`() = runTest {
        val junk = createAppJunk("com.apex.module", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        coEvery { inaccessibleCacheProvider.determineCache(any()) } returns nonZeroCache(junk)
        coEvery { noSettingsDetector.getUnreachableReason(junk.pkg) } returns NoSettingsDetector.Reason.NO_SETTINGS_PAGE

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        // Pre-filter is gated inside the useAutomation branch; it must not inject a failure
        // when automation is disabled.
        result.failed.shouldBeEmpty()
        result.succesful shouldNotContain junk.identifier
        coVerify(exactly = 0) { automationManager.submit(any()) }
    }

    @Test
    fun `a terminal ACS failure still reports the caches it already cleared`() = runTest {
        // The service clears one app and then dies on the next. That first cache is genuinely empty
        // now, so it has to reach the caller even though the call ends in an exception, or the
        // dashboard will offer to free those bytes again on the next render.
        val cleared = createAppJunk("com.example.cleared", cacheSize = 100000)
        val doomed = createAppJunk("com.example.doomed", cacheSize = 200000)
        val snapshot = createSnapshot(cleared, doomed)

        coEvery { inaccessibleCacheProvider.determineCache(cleared.pkg) } returns nonZeroCache(cleared)
        coEvery { inaccessibleCacheProvider.determineCache(doomed.pkg) } returns nonZeroCache(doomed)
        coEvery { noSettingsDetector.getUnreachableReason(any()) } returns null
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)

        val boom = AutomationCompatibilityException()
        coEvery { automationManager.submit(match { it is ClearCacheTask }) } answers {
            firstArg<ClearCacheTask>().onSuccess(cleared.identifier)
            throw boom
        }

        var partial: InaccessibleDeleter.InaccDelResult? = null
        shouldThrow<AutomationCompatibilityException> {
            deleter.deleteInaccessible(
                snapshot = snapshot,
                targetPkgs = null,
                useAutomation = true,
                isBackground = false,
                onPartialResult = { partial = it },
            )
        } shouldBe boom

        partial.shouldNotBeNull()
        partial!!.succesful shouldContain cleared.identifier
        partial!!.failed shouldNotContainKey cleared.identifier
    }

    @Test
    fun `mixed batch - only no-settings target is pre-flight failed, rest go to ACS`() = runTest {
        val junkNoSettings = createAppJunk("com.apex.module", cacheSize = 100000)
        val junkNormal = createAppJunk("com.example.normal", cacheSize = 200000)
        val snapshot = createSnapshot(junkNoSettings, junkNormal)

        coEvery { inaccessibleCacheProvider.determineCache(junkNoSettings.pkg) } returns nonZeroCache(junkNoSettings)
        coEvery { inaccessibleCacheProvider.determineCache(junkNormal.pkg) } returns nonZeroCache(junkNormal)
        coEvery { noSettingsDetector.getUnreachableReason(junkNoSettings.pkg) } returns NoSettingsDetector.Reason.NO_SETTINGS_PAGE
        coEvery { noSettingsDetector.getUnreachableReason(junkNormal.pkg) } returns null
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)

        val capturedTask = slot<ClearCacheTask>()
        coEvery { automationManager.submit(capture(capturedTask)) } returns ClearCacheTask.Result(
            successful = setOf(junkNormal.identifier),
            failed = emptyMap(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.failed shouldContainKey junkNoSettings.identifier
        result.failed[junkNoSettings.identifier].shouldBeInstanceOf<NoSettingsWindowException>()
        result.succesful shouldContain junkNormal.identifier
        result.failed shouldNotContainKey junkNormal.identifier

        // ACS was called exactly once — with only the normal target
        coVerify(exactly = 1) { automationManager.submit(any()) }
        capturedTask.captured.targets shouldBe listOf(junkNormal.identifier)
    }

    @Test
    fun `all-no-settings batch - ACS is not called at all`() = runTest {
        val junk1 = createAppJunk("com.apex.one", cacheSize = 100000)
        val junk2 = createAppJunk("com.apex.two", cacheSize = 200000)
        val snapshot = createSnapshot(junk1, junk2)

        coEvery { inaccessibleCacheProvider.determineCache(junk1.pkg) } returns nonZeroCache(junk1)
        coEvery { inaccessibleCacheProvider.determineCache(junk2.pkg) } returns nonZeroCache(junk2)
        coEvery { noSettingsDetector.getUnreachableReason(any()) } returns NoSettingsDetector.Reason.NO_SETTINGS_PAGE
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.failed shouldContainKey junk1.identifier
        result.failed shouldContainKey junk2.identifier
        result.failed[junk1.identifier].shouldBeInstanceOf<NoSettingsWindowException>()
        result.failed[junk2.identifier].shouldBeInstanceOf<NoSettingsWindowException>()
        result.succesful.size shouldBe 0
        // Empty-batch guard: no submission even though useAutomation=true
        coVerify(exactly = 0) { automationManager.submit(any()) }
    }

    /**
     * Drives the automation-based force-stop branch: no root/ADB, automation setup complete,
     * and force-stop-before-clearing enabled.
     */
    private fun enableAutomationForceStop() {
        every { rootManager.useRoot } returns flowOf(false)
        every { adbManager.useAdb } returns flowOf(false)
        every { automationSetupModule.state } returns flowOf(
            mockk<SetupModule.State.Current> { every { isComplete } returns true }
        )
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(true)
    }

    @Test
    fun `user cancel during force-stop aborts before clearing cache and keeps prior successes`() = runTest {
        val junkMain = createAppJunk("com.example.main", cacheSize = 100000)
        val junkZero = createAppJunk("com.example.empty", cacheSize = 80000)
        val snapshot = createSnapshot(junkMain, junkZero)

        enableAutomationForceStop()
        coEvery { inaccessibleCacheProvider.determineCache(junkMain.pkg) } returns nonZeroCache(junkMain)
        // junkZero already emptied out -> counts as success before force-stop runs
        coEvery { inaccessibleCacheProvider.determineCache(junkZero.pkg) } returns InaccessibleCache(
            identifier = junkZero.identifier,
            isSystemApp = false,
            itemCount = 0,
            totalSize = 0L,
            publicSize = 0L,
            theoreticalPaths = emptySet(),
        )

        coEvery { automationManager.submit(match { it is ForceStopAutomationTask }) } throws UserCancelledAutomationException()

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        // User cancel == full stop: cache clearing must NOT be attempted.
        coVerify(exactly = 0) { automationManager.submit(match { it is ClearCacheTask }) }
        // The actual force-stop target was not cleared.
        result.succesful shouldNotContain junkMain.identifier
        // A success that happened before the cancel is still reported (partial result).
        result.succesful shouldContain junkZero.identifier
    }

    @Test
    fun `non-cancel force-stop failure still proceeds to clear cache`() = runTest {
        val junk = createAppJunk("com.example.main", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        enableAutomationForceStop()
        coEvery { inaccessibleCacheProvider.determineCache(junk.pkg) } returns nonZeroCache(junk)

        coEvery { automationManager.submit(match { it is ForceStopAutomationTask }) } throws RuntimeException("force-stop boom")
        coEvery { automationManager.submit(match { it is ClearCacheTask }) } returns ClearCacheTask.Result(
            successful = setOf(junk.identifier),
            failed = emptyMap(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        // Force-stop is best-effort: a failure must not block cache clearing.
        coVerify(exactly = 1) { automationManager.submit(match { it is ClearCacheTask }) }
        result.succesful shouldContain junk.identifier
    }

    @Test
    fun `force-stop internal cancellation with active scope still clears cache`() = runTest {
        val junk = createAppJunk("com.example.main", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        enableAutomationForceStop()
        coEvery { inaccessibleCacheProvider.determineCache(junk.pkg) } returns nonZeroCache(junk)

        // Simulates retry-exhaustion: the automation processor's own job is cancelled, surfacing as a
        // plain CancellationException while OUR coroutine scope is still active. Must be treated as a
        // best-effort failure (continue), NOT as a user cancel (abort).
        coEvery { automationManager.submit(match { it is ForceStopAutomationTask }) } throws CancellationException("automation job cancelled")
        coEvery { automationManager.submit(match { it is ClearCacheTask }) } returns ClearCacheTask.Result(
            successful = setOf(junk.identifier),
            failed = emptyMap(),
        )

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        coVerify(exactly = 1) { automationManager.submit(match { it is ClearCacheTask }) }
        result.succesful shouldContain junk.identifier
    }

    // ─────────────────────────── freed-byte measurement ───────────────────────────
    // A backend reporting "cache cleared" is a claim, not a measurement: the app in the report
    // that started this said success three runs in a row and never gave up a single byte. These
    // cover what the settle poll reports, and that it never reclassifies anything.

    /**
     * Feeds `determineCache` a per-package script of sizes, one entry per read. The last entry
     * repeats forever, so a test only spells out the reads that differ. A `null` entry is a
     * failed query.
     */
    private fun scriptCacheSizes(vararg scripts: Pair<AppJunk, List<Long?>>) {
        val reads = mutableMapOf<String, Int>()
        scripts.forEach { (junk, sizes) ->
            coEvery { inaccessibleCacheProvider.determineCache(junk.pkg) } answers {
                val index = reads.merge(junk.identifier.pkgId.name, 1, Int::plus)!! - 1
                sizes[minOf(index, sizes.lastIndex)]?.let { size ->
                    InaccessibleCache(
                        identifier = junk.identifier,
                        isSystemApp = false,
                        itemCount = if (size == 0L) 0 else 1,
                        totalSize = size,
                        publicSize = 0L,
                        theoreticalPaths = emptySet(),
                    )
                }
            }
        }
    }

    /** Lets the ACS stage run and report every given target as cleared. */
    private fun acsClears(vararg targets: AppJunk) {
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)
        coEvery { automationManager.submit(match { it is ClearCacheTask }) } returns ClearCacheTask.Result(
            successful = targets.map { it.identifier }.toSet(),
            failed = emptyMap(),
        )
    }

    @Test
    fun `freed bytes are measured per app, an app that kept its cache reports zero`() = runTest {
        val dropped = createAppJunk("com.example.dropped", cacheSize = 100000)
        val stubborn = createAppJunk("com.example.stubborn", cacheSize = 200000)
        val snapshot = createSnapshot(dropped, stubborn)

        // First read each is the pre-ACS re-query, everything after it is the observation.
        scriptCacheSizes(
            dropped to listOf(100000L, 0L),
            stubborn to listOf(200000L),
        )
        acsClears(dropped, stubborn)

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.freedBytes[dropped.identifier] shouldBe 100000L
        result.freedBytes[stubborn.identifier] shouldBe 0L
        // Number-only: an app that gave up nothing is still a success and gains no error.
        result.succesful shouldContain dropped.identifier
        result.succesful shouldContain stubborn.identifier
        result.failed.shouldBeEmpty()
    }

    @Test
    fun `an unreadable cache falls back to the pre-clear size and says so`() = runTest {
        val junk = createAppJunk("com.example.unreadable", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        // Cleared fine, but every size read after that fails.
        scriptCacheSizes(junk to listOf(100000L, null))
        acsClears(junk)

        val logs = collectLogs()
        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        // Reporting 0 would tell a user who really did get their space back that nothing happened.
        result.freedBytes[junk.identifier] shouldBe 100000L
        result.succesful shouldContain junk.identifier
        // An unmeasured number must not pass for a measured one in a debug log.
        logs.any { it.contains("Freed-byte read failed, falling back to pre-clear size") } shouldBe true
    }

    @Test
    fun `a cache still at its pre-clear size keeps polling until the budget runs out`() = runTest {
        // Two identical reads are not proof of a settled number while the size still equals what it
        // was before the clear: StorageStatsManager lags, so both can be the same stale value.
        // Accepting that would report 0 freed for a cache that did clear.
        val junk = createAppJunk("com.example.unchanged", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        scriptCacheSizes(junk to listOf(100000L))
        acsClears(junk)

        val logs = collectLogs()
        val startedAt = currentTime
        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        // Virtual time, so this costs no wall-clock: the poll ran for the full settle budget and
        // then gave up instead of hanging.
        (currentTime - startedAt) shouldBeGreaterThanOrEqual 10_000L
        logs.any { it.contains("Freed-byte observation timed out") } shouldBe true
        result.freedBytes[junk.identifier] shouldBe 0L
        result.succesful shouldContain junk.identifier
        result.failed.shouldBeEmpty()
    }

    @Test
    fun `a size that only drops after two stale reads still reports the full baseline`() = runTest {
        // The pre-ACS re-query plus two post-clear reads all return the pre-clear size, then the
        // number finally catches up. Settling on the repeated stale value would report 0 freed for
        // a cache that gave up every byte.
        val junk = createAppJunk("com.example.laggy", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        scriptCacheSizes(junk to listOf(100000L, 100000L, 100000L, 0L))
        acsClears(junk)

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.freedBytes[junk.identifier] shouldBe 100000L
        result.succesful shouldContain junk.identifier
    }

    @Test
    fun `a read that fails after a successful one falls back to the pre-clear size`() = runTest {
        // 100000 -> 90000 -> unreadable. The 90000 was a number on the way down, not a result, so
        // crediting it would report 10000 freed for a cache we can no longer measure.
        val junk = createAppJunk("com.example.halfread", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        scriptCacheSizes(junk to listOf(100000L, 90000L, null))
        acsClears(junk)

        val logs = collectLogs()
        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.freedBytes[junk.identifier] shouldBe 100000L
        result.succesful shouldContain junk.identifier
        logs.any { it.contains("Freed-byte read failed, falling back to pre-clear size") } shouldBe true
    }

    @Test
    fun `freed bytes follow the smallest observed size, not the first decrease`() = runTest {
        // StorageStatsManager walks down towards its final value. Settling on the first decrease
        // would turn a 100000 -> 90000 -> 0 clear into "10000 freed", the same class of wrong
        // number this whole measurement exists to remove.
        val junk = createAppJunk("com.example.stepwise", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        scriptCacheSizes(junk to listOf(100000L, 90000L, 0L))
        acsClears(junk)

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.freedBytes[junk.identifier] shouldBe 100000L
    }

    @Test
    fun `a cancel during the observation forwards the caches that were cleared`() = runTest {
        // The observation suspends after the ACS successes are already banked but before they are
        // returned. Without the partial-result hand-off the caller ends up with no result at all
        // and keeps offering to free caches that are genuinely gone.
        val junk = createAppJunk("com.example.cleared", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        acsClears(junk)

        var running: Deferred<InaccessibleDeleter.InaccDelResult>? = null
        var reads = 0
        coEvery { inaccessibleCacheProvider.determineCache(junk.pkg) } answers {
            reads++
            // Read 1 is the pre-ACS re-query. From read 2 we are inside the observation, and a
            // non-settling size keeps it there long enough to hit the delay between rounds.
            if (reads >= 2) running!!.cancel()
            InaccessibleCache(
                identifier = junk.identifier,
                isSystemApp = false,
                itemCount = 1,
                totalSize = if (reads >= 2) 90000L else 100000L,
                publicSize = 0L,
                theoreticalPaths = emptySet(),
            )
        }

        var partial: InaccessibleDeleter.InaccDelResult? = null
        val deferred = async {
            deleter.deleteInaccessible(
                snapshot = snapshot,
                targetPkgs = null,
                useAutomation = true,
                isBackground = false,
                onPartialResult = { partial = it },
            )
        }
        running = deferred

        shouldThrow<CancellationException> { deferred.await() }

        partial.shouldNotBeNull()
        partial!!.succesful shouldContain junk.identifier
    }

    @Test
    fun `every target is sampled at least once even when one pass outlasts the budget`() = runTest {
        // A budget applied from the very first read would expire part-way through a big batch and
        // leave the late targets on the optimistic pre-clear figure without anyone noticing.
        val targets = (1..12).map { createAppJunk("com.example.batch$it", cacheSize = 100000) }
        val snapshot = createSnapshot(*targets.toTypedArray())

        acsClears(*targets.toTypedArray())

        val reads = mutableMapOf<String, Int>()
        coEvery { inaccessibleCacheProvider.determineCache(any()) } coAnswers {
            val pkg = firstArg<Installed>()
            // 12 targets at 1s of query latency each outlast the settle budget on their own.
            delay(1000)
            val seen = reads.merge(pkg.packageName, 1, Int::plus)!!
            InaccessibleCache(
                identifier = InstallId(pkg.id, testHandle),
                isSystemApp = false,
                itemCount = 1,
                totalSize = if (seen == 1) 100000L else 25000L,
                publicSize = 0L,
                theoreticalPaths = emptySet(),
            )
        }

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.freedBytes.size shouldBe 12
        // 75000, not 100000: an unsampled target would have fallen back to its pre-clear size.
        targets.forEach { result.freedBytes[it.identifier] shouldBe 75000L }
    }

    @Test
    fun `an ADB-cleared target is measured against its scan-time size`() = runTest {
        val junk = createAppJunk("com.example.adb", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        every { adbManager.useAdb } returns flowOf(true)
        coEvery { pkgOps.trimCaches(any(), any(), any()) } returns Unit
        // ADB freed 60000 of the 100000 the scan saw. The pre-ACS re-query never runs for a target
        // ADB already cleared, so the scan-time size stays the baseline.
        scriptCacheSizes(junk to listOf(40000L))

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = false,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
        result.freedBytes[junk.identifier] shouldBe 60000L
    }

    @Test
    fun `a cache that was already empty is not measured and keeps its scan-time size`() = runTest {
        val junk = createAppJunk("com.example.alreadyempty", cacheSize = 100000)
        val snapshot = createSnapshot(junk)

        scriptCacheSizes(junk to listOf(0L))

        val result = deleter.deleteInaccessible(
            snapshot = snapshot,
            targetPkgs = null,
            useAutomation = true,
            isBackground = false,
        )

        result.succesful shouldContain junk.identifier
        // No entry means the caller falls back to the scan-time size, i.e. unchanged behaviour.
        result.freedBytes shouldNotContainKey junk.identifier
        // Only the pre-ACS re-query, no observation reads on top of it.
        coVerify(exactly = 1) { inaccessibleCacheProvider.determineCache(junk.pkg) }
    }
}
