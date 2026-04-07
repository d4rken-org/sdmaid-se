package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.automation.ClearCacheTask
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.automation.core.AutomationSubmitter
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.pkgs.NoSettingsDetector
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.collections.shouldContain
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue

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
        every { noSettingsDetector.hasNoSettings(any()) } returns false

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

    private fun createAppJunk(
        pkgName: String,
        cacheSize: Long,
    ): AppJunk {
        val installId = InstallId(Pkg.Id(pkgName), testHandle)
        val pkg = mockk<Installed> {
            every { id } returns Pkg.Id(pkgName)
            every { userHandle } returns testHandle
            every { this@mockk.installId } returns installId
            every { packageName } returns pkgName
            every { label } returns null
        }
        val cache = InaccessibleCache(
            identifier = installId,
            isSystemApp = false,
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
        every { noSettingsDetector.hasNoSettings(junk.pkg) } returns true
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
        every { noSettingsDetector.hasNoSettings(junk.pkg) } returns true

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
    fun `mixed batch - only no-settings target is pre-flight failed, rest go to ACS`() = runTest {
        val junkNoSettings = createAppJunk("com.apex.module", cacheSize = 100000)
        val junkNormal = createAppJunk("com.example.normal", cacheSize = 200000)
        val snapshot = createSnapshot(junkNoSettings, junkNormal)

        coEvery { inaccessibleCacheProvider.determineCache(junkNoSettings.pkg) } returns nonZeroCache(junkNoSettings)
        coEvery { inaccessibleCacheProvider.determineCache(junkNormal.pkg) } returns nonZeroCache(junkNormal)
        every { noSettingsDetector.hasNoSettings(junkNoSettings.pkg) } returns true
        every { noSettingsDetector.hasNoSettings(junkNormal.pkg) } returns false
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
        every { noSettingsDetector.hasNoSettings(any()) } returns true
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
}
