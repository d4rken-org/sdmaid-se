package eu.darken.sdmse.appcontrol.core.automation.specs.oxygenos

import android.graphics.Rect
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.appcontrol.core.automation.specs.aosp.AOSPSpecs
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.debug.Bugs
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.TestApplication
import testhelpers.mockDataStoreValue
import testhelpers.automation.TestAutomationHost

/**
 * Regression coverage for the OxygenOS force-stop confirmation dialog: a COUI dialog with no title
 * node at all, whose positive button (`android:id/button1`) repeats the action verb instead of
 * carrying the AOSP "OK" label. Under the AOSP fallback the confirmation step's window check never
 * matched and the step timed out.
 *
 * Robolectric so `android.graphics.Rect` (node bounds) actually works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class OxygenOSSpecsTest : BaseTest() {

    private val ipcFunnel: IPCFunnel = mockk(relaxed = true)
    private val deviceDetective: DeviceDetective = mockk(relaxed = true)
    private val generalSettings: GeneralSettings = mockk(relaxed = true)
    private val stepper: Stepper = mockk(relaxed = true)
    private val aospSpecs: AOSPSpecs = mockk(relaxed = true)
    private val labels: AOSPLabels = mockk {
        every { getForceStopButtonDynamic(any()) } returns setOf("Force stop")
        every { getForceStopDialogTitleDynamic(any()) } returns setOf("Force stop?")
        every { getForceStopDialogOkDynamic(any()) } returns setOf("OK")
        every { getForceStopDialogCancelDynamic(any()) } returns setOf("Cancel")
    }

    @After
    fun resetDryRun() {
        Bugs.isDryRun = false
    }

    private fun createSpec() = OxygenOSSpecs(
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        aospLabels = labels,
        aospSpecs = aospSpecs,
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

    private fun settingsRoot() = TestACSNodeInfo(
        viewIdResourceName = "root",
        packageName = "com.android.settings",
        bounds = Rect(0, 0, 1080, 2400),
    )

    /** App-info screen: the Force stop action is a plain clickable Button (id/middle_button). */
    private fun appInfoScreen(): Pair<TestACSNodeInfo, TestACSNodeInfo> {
        val root = settingsRoot()
        val forceStop = TestACSNodeInfo(
            text = "Force stop",
            className = "android.widget.Button",
            viewIdResourceName = "com.android.settings:id/middle_button",
            isClickable = true,
            bounds = Rect(58, 691, 540, 755),
        )
        root.addChild(forceStop)
        return root to forceStop
    }

    private data class PlanRun(
        val processed: List<String>,
        val confirmWindowCheckPassed: Boolean,
        val confirmActionResult: Boolean?,
    )

    /**
     * Runs the real force-stop plan: the App-info step's nodeAction runs against [appInfoRoot],
     * then the window root swaps to [dialogRoot] and the confirmation step's windowCheck and
     * nodeAction both run against it.
     */
    private suspend fun runForceStopPlan(
        scope: TestScope,
        appInfoRoot: TestACSNodeInfo,
        dialogRoot: TestACSNodeInfo,
        pkg: Installed,
    ): PlanRun {
        val testHost = TestAutomationHost(scope).apply { setWindowRoot(appInfoRoot) }
        val context = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress = emptyFlow<Progress.Data?>()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }

        val processed = mutableListOf<String>()
        var windowCheckPassed = false
        var actionResult: Boolean? = null

        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            processed += step.descriptionInternal
            val stepContext = StepContext(hostContext = context, tag = "test", stepAttempts = 0)
            if (step.descriptionInternal.startsWith("Confirm force stop")) {
                // windowCheck never emits while its condition is unmet, so a verdict needs a bound.
                windowCheckPassed = withTimeoutOrNull(1000) { step.windowCheck!!.invoke(stepContext) } != null
                actionResult = step.nodeAction?.invoke(stepContext)
            } else {
                step.nodeAction?.invoke(stepContext)
            }
            if (step.descriptionInternal.startsWith("Force stop button")) {
                testHost.setWindowRoot(dialogRoot)
            }
            Unit
        }

        val plan = (createSpec().getForceStop(pkg) as AutomationSpec.Explorer).createPlan()
        plan.invoke(context)
        return PlanRun(processed, windowCheckPassed, actionResult)
    }

    @Test
    fun `titleless COUI dialog is recognized and its force-stop button clicked`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val message = TestACSNodeInfo(
            text = "Force stopping an app may cause it to misbehave.",
            className = "android.widget.TextView",
            viewIdResourceName = "android:id/message",
            bounds = Rect(100, 900, 980, 1040),
        )
        val confirm = TestACSNodeInfo(
            text = "Force stop",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        val divider = TestACSNodeInfo(className = "android.widget.ImageView", bounds = Rect(540, 1100, 542, 1180))
        val cancel = TestACSNodeInfo(
            text = "Cancel",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button2",
            isClickable = true,
            bounds = Rect(100, 1100, 500, 1180),
        )
        dialogRoot.addChildren(message, confirm, divider, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        message.performedActions shouldBe emptyList()
        run.processed.size shouldBe 2
        run.processed[1].startsWith("Confirm force stop") shouldBe true
    }

    @Test
    fun `titled dialog with a stock OK button still works`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val confirm = TestACSNodeInfo(
            text = "OK",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        val cancel = TestACSNodeInfo(
            text = "Cancel",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button2",
            isClickable = true,
            bounds = Rect(100, 1100, 500, 1180),
        )
        dialogRoot.addChildren(title, confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        title.performedActions shouldBe emptyList()
        run.processed.size shouldBe 2
    }

    @Test
    fun `a title alone is enough to recognize the dialog`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val cancel = TestACSNodeInfo(
            text = "Cancel",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button2",
            isClickable = true,
            bounds = Rect(100, 1100, 500, 1180),
        )
        dialogRoot.addChildren(title, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
    }

    @Test
    fun `the app info screen is not mistaken for the confirmation dialog`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        // Same screen as step 1: a Force stop Button, but on id/middle_button and with no button1.
        val stillAppInfo = settingsRoot()
        val header = TestACSNodeInfo(text = "App info", className = "android.widget.TextView", bounds = Rect(100, 200, 980, 280))
        val forceStop = TestACSNodeInfo(
            text = "Force stop",
            className = "android.widget.Button",
            viewIdResourceName = "com.android.settings:id/middle_button",
            isClickable = true,
            bounds = Rect(58, 691, 540, 755),
        )
        stillAppInfo.addChildren(header, forceStop)

        val run = runForceStopPlan(this, appInfoRoot, stillAppInfo, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
        run.confirmActionResult shouldBe false
        forceStop.performedActions shouldBe emptyList()
        header.performedActions shouldBe emptyList()
    }

    @Test
    fun `an unrelated settings dialog is not recognized`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val message = TestACSNodeInfo(
            text = "Reset all app preferences?",
            className = "android.widget.TextView",
            viewIdResourceName = "android:id/message",
            bounds = Rect(100, 900, 980, 1040),
        )
        val confirm = TestACSNodeInfo(
            text = "OK",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        val cancel = TestACSNodeInfo(
            text = "Cancel",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button2",
            isClickable = true,
            bounds = Rect(100, 1100, 500, 1180),
        )
        dialogRoot.addChildren(message, confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
    }

    @Test
    fun `a title carrying the exact button text does not shadow the confirm button`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val confirm = TestACSNodeInfo(
            text = "Force stop",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        dialogRoot.addChildren(title, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        title.performedActions shouldBe emptyList()
    }

    @Test
    fun `a confirm control of a custom button class is clicked`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val confirm = TestACSNodeInfo(
            text = "Force stop",
            className = "com.coui.appcompat.button.COUIButton",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        dialogRoot.addChildren(title, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        title.performedActions shouldBe emptyList()
    }

    @Test
    fun `a label nested in a clickable wrapper clicks the wrapper`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val wrapper = TestACSNodeInfo(
            className = "android.widget.LinearLayout",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        val label = TestACSNodeInfo(text = "Force stop", className = "android.widget.TextView", bounds = Rect(600, 1120, 960, 1160))
        wrapper.addChild(label)
        dialogRoot.addChildren(title, wrapper)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        wrapper.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        label.performedActions shouldBe emptyList()
    }

    @Test
    fun `a dry run selects cancel instead of confirming`() = runTest {
        Bugs.isDryRun = true
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val message = TestACSNodeInfo(
            text = "Force stopping an app may cause it to misbehave.",
            className = "android.widget.TextView",
            viewIdResourceName = "android:id/message",
            bounds = Rect(100, 900, 980, 1040),
        )
        val confirm = TestACSNodeInfo(
            text = "Force stop",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button1",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        val cancel = TestACSNodeInfo(
            text = "Cancel",
            className = "android.widget.Button",
            viewIdResourceName = "android:id/button2",
            isClickable = true,
            bounds = Rect(100, 1100, 500, 1180),
        )
        dialogRoot.addChildren(message, confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        cancel.performedActions shouldBe listOf(ACSNodeInfo.ACTION_SELECT)
        confirm.performedActions shouldBe emptyList()
    }

    @Test
    fun `responsibility follows the rom type override`() = runTest {
        val pkg = createTestPkg()

        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.OXYGENOS)
        createSpec().isResponsible(pkg) shouldBe true

        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.COLOROS)
        createSpec().isResponsible(pkg) shouldBe false

        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.AOSP)
        createSpec().isResponsible(pkg) shouldBe false
    }

    @Test
    fun `responsibility falls back to device detection when set to auto`() = runTest {
        val pkg = createTestPkg()
        every { generalSettings.romTypeDetection } returns mockDataStoreValue(RomType.AUTO)

        every { deviceDetective.getROMType() } returns RomType.OXYGENOS
        createSpec().isResponsible(pkg) shouldBe true

        every { deviceDetective.getROMType() } returns RomType.COLOROS
        createSpec().isResponsible(pkg) shouldBe false

        every { deviceDetective.getROMType() } returns RomType.AOSP
        createSpec().isResponsible(pkg) shouldBe false
    }
}
