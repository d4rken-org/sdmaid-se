package eu.darken.sdmse.appcontrol.core.automation.specs.coloros

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
import testhelpers.automation.TestAutomationHost

/**
 * Regression coverage for the ColorOS force-stop confirmation dialog, whose positive button
 * repeats the action verb ("Force stop" / "Paksa berhenti") instead of the AOSP "OK" label.
 * Under the previous AOSP fallback the confirmation step never matched and timed out.
 *
 * Robolectric so `android.graphics.Rect` (node bounds) actually works.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ColorOSSpecsTest : BaseTest() {

    private val ipcFunnel: IPCFunnel = mockk(relaxed = true)
    private val deviceDetective: DeviceDetective = mockk(relaxed = true)
    private val generalSettings: GeneralSettings = mockk(relaxed = true)
    private val stepper: Stepper = mockk(relaxed = true)
    private val aospSpecs: AOSPSpecs = mockk(relaxed = true)
    private var forceStopLabels = setOf("Paksa berhenti")
    private var dialogTitleLabels = setOf("Paksa berhenti")
    private var okLabels = setOf("Oke")
    private var cancelLabels = setOf("Batal")
    private val labels: AOSPLabels = mockk {
        every { getForceStopButtonDynamic(any()) } answers { forceStopLabels }
        every { getForceStopDialogTitleDynamic(any()) } answers { dialogTitleLabels }
        every { getForceStopDialogOkDynamic(any()) } answers { okLabels }
        every { getForceStopDialogCancelDynamic(any()) } answers { cancelLabels }
    }

    /** English labels, with a dialog title that differs from the force-stop verb. */
    private fun useEnglishLabels() {
        forceStopLabels = setOf("Force stop")
        dialogTitleLabels = setOf("Force stop?")
        okLabels = setOf("OK")
        cancelLabels = setOf("Cancel")
    }

    @After
    fun resetDryRun() {
        Bugs.isDryRun = false
    }

    private fun createSpec() = ColorOSSpecs(
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

    private fun settingsRoot(packageName: String = "com.android.settings") = TestACSNodeInfo(
        viewIdResourceName = "root",
        packageName = packageName,
        bounds = Rect(0, 0, 1080, 2400),
    )

    /** App-info screen's Force stop action: a plain clickable Button (id/middle_button). */
    private fun appInfoForceStop() = TestACSNodeInfo(
        text = forceStopLabels.first(),
        className = "android.widget.Button",
        viewIdResourceName = "com.android.settings:id/middle_button",
        isClickable = true,
        bounds = Rect(58, 691, 540, 755),
    )

    private fun appInfoScreen(): Pair<TestACSNodeInfo, TestACSNodeInfo> {
        val root = settingsRoot()
        val forceStop = appInfoForceStop()
        root.addChild(forceStop)
        return root to forceStop
    }

    /** The titleless ColorOS dialog: a message and the framework AlertDialog buttons. */
    private fun dialogMessage(text: String = "Exceptions may occur if an app is force stopped.") = TestACSNodeInfo(
        text = text,
        className = "android.widget.TextView",
        viewIdResourceName = "android:id/message",
        bounds = Rect(100, 900, 980, 1040),
    )

    private fun dialogButton1(text: String = forceStopLabels.first(), isClickable: Boolean = true) = TestACSNodeInfo(
        text = text,
        className = "android.widget.Button",
        viewIdResourceName = "android:id/button1",
        isClickable = isClickable,
        bounds = Rect(580, 1100, 980, 1180),
    )

    private fun dialogButton2() = TestACSNodeInfo(
        text = cancelLabels.first(),
        className = "android.widget.Button",
        viewIdResourceName = "android:id/button2",
        isClickable = true,
        bounds = Rect(100, 1100, 500, 1180),
    )

    private data class PlanRun(
        val processed: List<String>,
        val confirmWindowCheckPassed: Boolean,
        val confirmActionResult: Boolean?,
    )

    /**
     * Runs the real force-stop plan: the App-info step's nodeAction runs against [appInfoRoot],
     * then the window root swaps to [dialogRoot] and the confirmation step's windowCheck runs
     * against it. The confirmation step's nodeAction runs against [actionRoot], [dialogRoot] by
     * default, so a dialog that vanishes between the check and the action can be modeled.
     */
    private suspend fun runForceStopPlan(
        scope: TestScope,
        appInfoRoot: TestACSNodeInfo,
        dialogRoot: TestACSNodeInfo,
        pkg: Installed,
        actionRoot: TestACSNodeInfo = dialogRoot,
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
                testHost.setWindowRoot(actionRoot)
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
    fun `confirmation button labeled with the force-stop verb is clicked instead of OK`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        // ColorOS dialog: title repeats the verb with a question mark, no "OK"/"Oke" anywhere.
        val dialogRoot = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 1080, 2400))
        val title = TestACSNodeInfo(text = "Paksa berhenti?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val cancel = TestACSNodeInfo(text = "Batal", className = "android.widget.Button", isClickable = true, bounds = Rect(100, 1100, 500, 1180))
        val confirm = TestACSNodeInfo(text = "Paksa berhenti", className = "android.widget.Button", isClickable = true, bounds = Rect(580, 1100, 980, 1180))
        dialogRoot.addChildren(title, cancel, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        title.performedActions shouldBe emptyList()
        run.confirmWindowCheckPassed shouldBe true
        run.processed.size shouldBe 2
        run.processed[1].startsWith("Confirm force stop") shouldBe true
    }

    @Test
    fun `dialog title carrying the exact button text does not shadow the confirm button`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        // Title text is IDENTICAL to the button text (no question mark): a title-exclusion filter
        // alone would reject both nodes; the button-first lookup must still find the Button.
        val dialogRoot = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 1080, 2400))
        val title = TestACSNodeInfo(text = "Paksa berhenti", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val cancel = TestACSNodeInfo(text = "Batal", className = "android.widget.Button", isClickable = true, bounds = Rect(100, 1100, 500, 1180))
        val confirm = TestACSNodeInfo(text = "Paksa berhenti", className = "android.widget.Button", isClickable = true, bounds = Rect(580, 1100, 980, 1180))
        dialogRoot.addChildren(title, cancel, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        title.performedActions shouldBe emptyList()
        run.confirmWindowCheckPassed shouldBe true
        run.processed.size shouldBe 2
    }

    @Test
    fun `stock OK confirmation button still works`() = runTest {
        val (appInfoRoot, _) = appInfoScreen()

        // ColorOS builds that use the stock label: confirm button is "Oke".
        val dialogRoot = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 1080, 2400))
        val title = TestACSNodeInfo(text = "Paksa berhenti?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val cancel = TestACSNodeInfo(text = "Batal", className = "android.widget.Button", isClickable = true, bounds = Rect(100, 1100, 500, 1180))
        val confirm = TestACSNodeInfo(text = "Oke", className = "android.widget.Button", isClickable = true, bounds = Rect(580, 1100, 980, 1180))
        dialogRoot.addChildren(title, cancel, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        run.confirmWindowCheckPassed shouldBe true
        run.processed.size shouldBe 2
    }

    @Test
    fun `titleless dialog is recognized and its button1 clicked`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val message = dialogMessage()
        val confirm = dialogButton1()
        val cancel = dialogButton2()
        dialogRoot.addChildren(message, confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        run.confirmActionResult shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        message.performedActions shouldBe emptyList()
        run.processed.size shouldBe 2
        run.processed[1].startsWith("Confirm force stop") shouldBe true
    }

    @Test
    fun `the app info screen is not mistaken for the confirmation dialog`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val stillAppInfo = settingsRoot()
        val header = TestACSNodeInfo(text = "App info", className = "android.widget.TextView", bounds = Rect(100, 200, 980, 280))
        val forceStop = appInfoForceStop()
        stillAppInfo.addChildren(header, forceStop)

        val run = runForceStopPlan(this, appInfoRoot, stillAppInfo, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
        run.confirmActionResult shouldBe false
        forceStop.performedActions shouldBe emptyList()
        header.performedActions shouldBe emptyList()
    }

    @Test
    fun `an unrelated settings dialog with an OK button1 is not recognized`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val message = dialogMessage(text = "Reset all app preferences?")
        val confirm = dialogButton1(text = "OK")
        val cancel = dialogButton2()
        dialogRoot.addChildren(message, confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
        run.confirmActionResult shouldBe false
        confirm.performedActions shouldBe emptyList()
        cancel.performedActions shouldBe emptyList()
    }

    @Test
    fun `a non-clickable button1 with the force-stop verb is not recognized`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val confirm = dialogButton1(isClickable = false)
        dialogRoot.addChildren(dialogMessage(), confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
        run.confirmActionResult shouldBe false
        confirm.performedActions shouldBe emptyList()
    }

    @Test
    fun `a dialog outside the settings package is not recognized`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot(packageName = "com.example.other")
        val confirm = dialogButton1()
        dialogRoot.addChildren(dialogMessage(), confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe false
        run.confirmActionResult shouldBe false
        confirm.performedActions shouldBe emptyList()
    }

    @Test
    fun `a dialog that vanishes after the window check is not confirmed via app info`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val confirm = dialogButton1()
        dialogRoot.addChildren(dialogMessage(), confirm)

        val backOnAppInfo = settingsRoot()
        val forceStop = appInfoForceStop()
        backOnAppInfo.addChild(forceStop)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg(), actionRoot = backOnAppInfo)

        run.confirmWindowCheckPassed shouldBe true
        run.confirmActionResult shouldBe false
        forceStop.performedActions shouldBe emptyList()
        confirm.performedActions shouldBe emptyList()
    }

    @Test
    fun `button1 is preferred over the app info force stop button in the same root`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        // The dialog's window can be reported together with App info underneath it.
        val dialogRoot = settingsRoot()
        val forceStop = appInfoForceStop()
        val confirm = dialogButton1()
        val cancel = dialogButton2()
        dialogRoot.addChildren(forceStop, dialogMessage(), confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        run.confirmActionResult shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        forceStop.performedActions shouldBe emptyList()
        cancel.performedActions shouldBe emptyList()
    }

    @Test
    fun `a confirm control of a custom button class is clicked`() = runTest {
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val title = TestACSNodeInfo(text = "Force stop?", className = "android.widget.TextView", bounds = Rect(100, 900, 980, 980))
        val confirm = TestACSNodeInfo(
            text = "Force stop",
            className = "com.coui.appcompat.button.COUIButton",
            viewIdResourceName = "com.android.settings:id/confirm",
            isClickable = true,
            bounds = Rect(580, 1100, 980, 1180),
        )
        dialogRoot.addChildren(title, confirm)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        run.confirmActionResult shouldBe true
        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        title.performedActions shouldBe emptyList()
    }

    @Test
    fun `a dry run clicks cancel instead of confirming`() = runTest {
        Bugs.isDryRun = true
        useEnglishLabels()
        val (appInfoRoot, _) = appInfoScreen()

        val dialogRoot = settingsRoot()
        val confirm = dialogButton1()
        val cancel = dialogButton2()
        dialogRoot.addChildren(dialogMessage(), confirm, cancel)

        val run = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        run.confirmWindowCheckPassed shouldBe true
        cancel.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        confirm.performedActions shouldBe emptyList()
    }
}
