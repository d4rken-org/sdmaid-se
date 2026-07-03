package eu.darken.sdmse.appcontrol.core.automation.specs.aosp

import android.graphics.Rect
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.hasApiLevel
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
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.TestApplication
import testhelpers.automation.TestAutomationHost

/**
 * Regression coverage for the AOSP force-stop plan on the Android 15+ App-Info action row, where
 * each action button is an icon + label pair and only the *enabled* buttons expose a clickable
 * wrapper. When Force stop is disabled (app already stopped) the old fallback grabbed the nearest
 * clickable — the Uninstall button — and opened the "Uninstall this app?" dialog.
 *
 * Robolectric so `android.graphics.Rect` (node bounds) actually works — the geometry is the point.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AOSPSpecsTest : BaseTest() {

    private lateinit var ipcFunnel: IPCFunnel
    private lateinit var deviceDetective: DeviceDetective
    private lateinit var generalSettings: GeneralSettings
    private lateinit var stepper: Stepper
    private lateinit var labels: AOSPLabels

    @Before
    fun setup() {
        ipcFunnel = mockk(relaxed = true)
        deviceDetective = mockk(relaxed = true)
        generalSettings = mockk(relaxed = true)
        stepper = mockk(relaxed = true)
        labels = mockk {
            every { getForceStopButtonDynamic(any()) } returns setOf("Force stop", "Beenden erzwingen")
            every { getForceStopDialogTitleDynamic(any()) } returns emptySet()
            every { getForceStopDialogOkDynamic(any()) } returns setOf("OK")
            every { getForceStopDialogCancelDynamic(any()) } returns setOf("Cancel")
        }
        // Android 15+ split action-row layout (Robolectric SDK is 33, so mock the level check).
        mockkStatic(::hasApiLevel)
        every { hasApiLevel(any()) } answers { firstArg<Int>() <= 35 }
    }

    @After
    fun cleanup() {
        // BaseTest's JUnit5 @AfterAll does not run under JUnit4/Robolectric.
        unmockkAll()
    }

    private fun createSpec() = AOSPSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        aospLabels = labels,
        generalSettings = generalSettings,
        stepper = stepper,
    )

    private fun createTestPkg(packageName: String = "com.superthomaslab.hueessentials"): Installed = mockk {
        every { installId } returns InstallId(
            pkgId = packageName.toPkgId(),
            userHandle = mockk<UserHandle2> { every { handleId } returns 0 },
        )
        every { this@mockk.packageName } returns packageName
        every { id } returns packageName.toPkgId()
    }

    private fun testContext(scope: TestScope, root: TestACSNodeInfo): AutomationExplorer.Context {
        val testHost = TestAutomationHost(scope).apply { setWindowRoot(root) }
        return object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress = emptyFlow<Progress.Data?>()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }
    }

    /**
     * Runs the real force-stop plan, executing only the "Force stop button" step's nodeAction
     * against [context]. Returns the descriptions of every step handed to the stepper (so callers
     * can assert whether the confirmation step was reached).
     */
    private suspend fun runForceStopPlan(context: AutomationExplorer.Context, pkg: Installed): List<String> {
        val processed = mutableListOf<String>()
        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            processed += step.descriptionInternal
            if (step.descriptionInternal.startsWith("Force stop button")) {
                val stepContext = StepContext(hostContext = context, tag = "test", stepAttempts = 0)
                step.nodeAction?.let { action ->
                    for (i in 0 until 5) {
                        if (action.invoke(stepContext)) break
                    }
                }
            }
            Unit
        }

        val plan = (createSpec().getForceStop(pkg) as AutomationSpec.Explorer).createPlan()
        plan.invoke(context)
        return processed
    }

    /** Uninstall is the only clickable in this row (Force stop disabled -> no clickable wrapper). */
    private fun disabledForceStopRow(): Pair<TestACSNodeInfo, TestACSNodeInfo> {
        val root = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 900))
        val uninstall = TestACSNodeInfo(viewIdResourceName = "uninstall", isClickable = true, bounds = Rect(332, 649, 628, 828))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 906, 873))
        row.addChildren(uninstall, fsLabel)
        root.addChild(row)
        return root to uninstall
    }

    @Test
    fun `force stop on already-stopped app does not click Uninstall and skips confirmation`() = runTest {
        val (root, uninstall) = disabledForceStopRow()
        val context = testContext(this, root)

        val processed = runForceStopPlan(context, createTestPkg())

        // The Uninstall button must never be clicked.
        uninstall.performedActions shouldBe emptyList()
        // Only the force-stop step runs; the confirmation step is skipped (button was disabled).
        processed.size shouldBe 1
        processed.single().startsWith("Force stop button") shouldBe true
    }

    @Test
    fun `force stop clicks the column-aligned force-stop button, not the Uninstall neighbour`() = runTest {
        val root = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 900))
        val uninstall = TestACSNodeInfo(viewIdResourceName = "uninstall", isClickable = true, bounds = Rect(332, 649, 628, 828))
        val fsClickable = TestACSNodeInfo(viewIdResourceName = "fs_clickable", isClickable = true, bounds = Rect(646, 649, 906, 828))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 906, 873))
        row.addChildren(uninstall, fsClickable, fsLabel)
        root.addChild(row)
        val context = testContext(this, root)

        val processed = runForceStopPlan(context, createTestPkg())

        fsClickable.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        uninstall.performedActions shouldBe emptyList()
        // Force stop was clicked -> the confirmation step is reached.
        processed.size shouldBe 2
        processed[1].startsWith("Confirm force stop button") shouldBe true
    }

    @Test
    fun `force stop clicks an enabled button stacked above its label (not treated as disabled)`() = runTest {
        // "Button over text" layout: an ENABLED Force stop button sits directly above its
        // non-overlapping label. Must be clicked, NOT misreported as already-stopped.
        val root = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 1080, 2400))
        val fsClickable = TestACSNodeInfo(viewIdResourceName = "fs_clickable", isClickable = true, bounds = Rect(691, 959, 869, 1098))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(628, 1113, 931, 1181))
        val action = TestACSNodeInfo(viewIdResourceName = "action", bounds = Rect(540, 900, 1020, 1240))
        action.addChildren(fsClickable, fsLabel)
        root.addChild(action)
        val context = testContext(this, root)

        val processed = runForceStopPlan(context, createTestPkg())

        fsClickable.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        processed.size shouldBe 2
    }
}
