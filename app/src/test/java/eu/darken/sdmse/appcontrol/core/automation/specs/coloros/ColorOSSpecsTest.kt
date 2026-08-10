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
    private val labels: AOSPLabels = mockk {
        every { getForceStopButtonDynamic(any()) } returns setOf("Paksa berhenti")
        every { getForceStopDialogTitleDynamic(any()) } returns setOf("Paksa berhenti")
        every { getForceStopDialogOkDynamic(any()) } returns setOf("Oke")
        every { getForceStopDialogCancelDynamic(any()) } returns setOf("Batal")
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

    /** App-info screen: the Force stop action is a plain clickable Button (id/middle_button). */
    private fun appInfoScreen(): Pair<TestACSNodeInfo, TestACSNodeInfo> {
        val root = TestACSNodeInfo(viewIdResourceName = "root", packageName = "com.android.settings", bounds = Rect(0, 0, 1080, 2400))
        val forceStop = TestACSNodeInfo(
            text = "Paksa berhenti",
            className = "android.widget.Button",
            viewIdResourceName = "com.android.settings:id/middle_button",
            isClickable = true,
            bounds = Rect(58, 691, 540, 755),
        )
        root.addChild(forceStop)
        return root to forceStop
    }

    /**
     * Runs the real force-stop plan: the App-info step's nodeAction runs against [appInfoRoot],
     * then the window root swaps to [dialogRoot] and the confirmation step's nodeAction runs
     * against it. Returns the descriptions of every step handed to the stepper.
     */
    private suspend fun runForceStopPlan(
        scope: TestScope,
        appInfoRoot: TestACSNodeInfo,
        dialogRoot: TestACSNodeInfo,
        pkg: Installed,
    ): List<String> {
        val testHost = TestAutomationHost(scope).apply { setWindowRoot(appInfoRoot) }
        val context = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress = emptyFlow<Progress.Data?>()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }

        val processed = mutableListOf<String>()
        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            processed += step.descriptionInternal
            val stepContext = StepContext(hostContext = context, tag = "test", stepAttempts = 0)
            step.nodeAction?.invoke(stepContext)
            if (step.descriptionInternal.startsWith("Force stop button")) {
                testHost.setWindowRoot(dialogRoot)
            }
            Unit
        }

        val plan = (createSpec().getForceStop(pkg) as AutomationSpec.Explorer).createPlan()
        plan.invoke(context)
        return processed
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

        val processed = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        title.performedActions shouldBe emptyList()
        processed.size shouldBe 2
        processed[1].startsWith("Confirm force stop") shouldBe true
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

        val processed = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        title.performedActions shouldBe emptyList()
        processed.size shouldBe 2
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

        val processed = runForceStopPlan(this, appInfoRoot, dialogRoot, createTestPkg())

        confirm.performedActions shouldBe listOf(ACSNodeInfo.ACTION_CLICK)
        cancel.performedActions shouldBe emptyList()
        processed.size shouldBe 2
    }
}
