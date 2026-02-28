package eu.darken.sdmse.appcleaner.core.automation.specs

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.GeneralSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.AcsDebugParser
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.automation.TestAutomationHost
import testhelpers.mockDataStoreValue

/**
 * Base test class for AppCleaner automation specs.
 *
 * Provides common test infrastructure:
 * - Shared mocks (IPCFunnel, DeviceDetective, Stepper, etc.)
 * - Test host and context setup
 * - Helper methods for capturing and running clear cache actions
 * - Generic test suites for `isResponsible()` and clear cache actions
 *
 * Usage:
 * ```kotlin
 * class MySpecsTest : BaseAppCleanerSpecTest<MySpecs, MyLabels>() {
 *     override val romType = RomType.MY_ROM
 *     override fun createSpec() = MySpecs(ipcFunnel, deviceDetective, labels, ...)
 *     override fun createLabels() = mockk<MyLabels>()
 *     override fun mockLabelDefaults() { ... }
 * }
 * ```
 */
abstract class BaseAppCleanerSpecTest<S : AppCleanerSpecGenerator, L : Any> : BaseTest() {

    // Common mocks - available to all spec tests
    protected lateinit var ipcFunnel: IPCFunnel
    protected lateinit var deviceDetective: DeviceDetective
    protected lateinit var storageEntryFinder: StorageEntryFinder
    protected lateinit var generalSettings: GeneralSettings
    protected lateinit var stepper: Stepper

    // Test automation host with event support
    protected lateinit var testHost: TestAutomationHost
    protected lateinit var testContext: AutomationExplorer.Context

    // For backward compatibility - use testHost.setWindowRoot() for new tests
    protected var testRoot: TestACSNodeInfo
        get() = testHost.getCurrentRoot() ?: TestACSNodeInfo()
        set(value) = testHost.setWindowRoot(value)

    // Spec-specific labels mock
    protected lateinit var labels: L

    // Test scope for coroutines - set during test execution
    private var currentTestScope: TestScope? = null

    // Abstract - must be implemented by each spec test
    abstract val romType: RomType
    abstract fun createSpec(): S
    abstract fun createLabels(): L
    abstract fun mockLabelDefaults()

    @BeforeEach
    fun baseSetup() {
        // Initialize common mocks
        ipcFunnel = mockk()
        deviceDetective = mockk()
        storageEntryFinder = mockk()
        generalSettings = mockk()
        stepper = mockk(relaxed = true)

        // Create spec-specific labels mock
        labels = createLabels()

        // TestAutomationHost will be initialized in setupTestScope()
        // For now, create a placeholder that will be replaced
        testHost = TestAutomationHost(TestScope())

        testContext = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress: Flow<Progress.Data?> = emptyFlow()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
                testHost.updateProgress(update)
            }
        }

        // Mock storage finder
        coEvery { storageEntryFinder.storageFinderAOSP(any(), any()) } returns mockk()

        // Setup default label mocks
        mockLabelDefaults()
    }

    /**
     * Setup the test scope for TestAutomationHost.
     * Call this at the start of tests that use event-based features.
     */
    protected fun setupTestScope(scope: TestScope) {
        currentTestScope = scope
        testHost = TestAutomationHost(scope)
        // Re-create testContext with the new host
        testContext = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress: Flow<Progress.Data?> = emptyFlow()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
                testHost.updateProgress(update)
            }
        }
    }

    /**
     * Creates a mock [Installed] package for testing.
     */
    protected fun createTestPkg(packageName: String = "test.pkg"): Installed = mockk {
        every { installId } returns InstallId(
            pkgId = packageName.toPkgId(),
            userHandle = mockk<UserHandle2> { every { handleId } returns 0 },
        )
        every { this@mockk.packageName } returns packageName
        every { applicationInfo } returns null
        every { id } returns packageName.toPkgId()
    }

    /**
     * Builds a test tree from ACS debug log format.
     */
    protected fun buildTestTree(acsLog: String): TestACSNodeInfo {
        return AcsDebugParser.parseTree(acsLog) ?: TestACSNodeInfo()
    }

    /**
     * Captures and runs the clear cache action from the spec.
     * Returns the result of the nodeAction.
     */
    protected suspend fun captureAndRunClearCacheAction(
        spec: S = createSpec(),
        pkg: Installed = createTestPkg(),
    ): Boolean {
        var actionResult = false

        // Capture the step when stepper.process is called and run the clear cache action
        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            // Run nodeAction for steps that contain "cache" in description (case insensitive)
            if (step.descriptionInternal.contains("cache", ignoreCase = true)) {
                val stepContext = StepContext(
                    hostContext = testContext,
                    tag = "test",
                    stepAttempts = 0,
                )
                val nodeAction = step.nodeAction
                if (nodeAction != null) {
                    try {
                        for (i in 0 until 10) {
                            val result = nodeAction.invoke(stepContext)
                            if (result) {
                                actionResult = true
                                break
                            }
                        }
                    } catch (_: StepAbortException) {
                        actionResult = false
                    }
                }
            }
            Unit
        }

        val automationSpec = spec.getClearCache(pkg) as AutomationSpec.Explorer
        val plan = automationSpec.createPlan()
        plan.invoke(testContext)

        return actionResult
    }

    // ============================================================
    // Generic isResponsible() tests - work for any ROM spec
    // ============================================================

    @Test
    fun `isResponsible returns true when romType matches`() = runTest {
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(romType)

        val spec = createSpec()
        val result = spec.isResponsible(createTestPkg())

        result shouldBe true
    }

    @Test
    open fun `isResponsible returns true when AUTO and device matches`() = runTest {
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.AUTO)
        coEvery { deviceDetective.getROMType() } returns romType

        val spec = createSpec()
        val result = spec.isResponsible(createTestPkg())

        result shouldBe true
    }

    @Test
    fun `isResponsible returns false when different romType set`() = runTest {
        // Use a different ROM type than the one this spec handles
        val differentRom = RomType.entries.first { it != romType && it != RomType.AUTO }
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(differentRom)

        val spec = createSpec()
        val result = spec.isResponsible(createTestPkg())

        result shouldBe false
    }

    @Test
    fun `isResponsible returns false when AUTO and device is different`() = runTest {
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.AUTO)
        // Return a different ROM type from device detection
        val differentRom = RomType.entries.first { it != romType && it != RomType.AUTO }
        coEvery { deviceDetective.getROMType() } returns differentRom

        val spec = createSpec()
        val result = spec.isResponsible(createTestPkg())

        result shouldBe false
    }

}
