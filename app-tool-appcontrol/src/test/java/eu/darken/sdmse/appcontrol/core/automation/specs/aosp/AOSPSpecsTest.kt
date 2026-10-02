package eu.darken.sdmse.appcontrol.core.automation.specs.aosp

import eu.darken.sdmse.appcontrol.core.archive.ArchiveUnavailableException
import eu.darken.sdmse.appcontrol.core.restore.RestoreUnavailableException
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.common.stepper.Stepper
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.GeneralSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class AOSPSpecsTest : BaseTest() {

    private val pkg = mockk<Installed>(relaxed = true).apply {
        every { id } returns Pkg.Id("eu.thlab.target")
        every { installId } returns InstallId(Pkg.Id("eu.thlab.target"), UserHandle2(handleId = 0))
    }

    private val aospLabels = mockk<AOSPLabels>().apply {
        every { getArchiveButtonDynamic(any()) } returns setOf("Archive")
        every { getRestoreButtonDynamic(any()) } returns setOf("Restore")
    }

    private val nodeActionResults = mutableListOf<Boolean>()

    // Runs each step's node action once against the current window, skipping launch/check/recovery.
    private val stepper = mockk<Stepper>().apply {
        every { progress } returns emptyFlow()
        coEvery { process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            val stepContext = StepContext(hostContext = firstArg(), tag = step.source, stepAttempts = 0)
            nodeActionResults.add(step.nodeAction!!(stepContext))
        }
    }

    private val specs = AOSPSpecs(
        ipcFunnel = mockk<IPCFunnel>(),
        deviceDetective = mockk<DeviceDetective>(),
        aospLabels = aospLabels,
        generalSettings = mockk<GeneralSettings>(),
        stepper = stepper,
    )

    private class Window(
        val root: ACSNodeInfo,
        val button: ACSNodeInfo,
    )

    // root -> clickable button wrapper -> unclickable label, as in the App info action row
    private fun window(label: String, buttonEnabled: Boolean): Window {
        val root = mockk<ACSNodeInfo>(relaxed = true)
        val button = mockk<ACSNodeInfo>(relaxed = true)
        val labelNode = mockk<ACSNodeInfo>(relaxed = true).apply {
            every { text } returns label
            every { isClickable } returns false
            every { parent } returns button
            every { childCount } returns 0
        }
        button.apply {
            every { text } returns null
            every { isClickable } returns true
            every { isEnabled } returns buttonEnabled
            every { parent } returns root
            every { childCount } returns 1
            every { getChild(0) } returns labelNode
            every { performAction(any()) } returns true
        }
        root.apply {
            every { text } returns null
            every { parent } returns null
            every { childCount } returns 1
            every { getChild(0) } returns button
        }
        return Window(root = root, button = button)
    }

    private fun contextFor(window: Window): AutomationExplorer.Context {
        val automationHost = mockk<AutomationHost>(relaxed = true).apply {
            coEvery { windowRoot() } returns window.root
        }
        return mockk<AutomationExplorer.Context>(relaxed = true).apply {
            every { host } returns automationHost
        }
    }

    private suspend fun AutomationSpec.runPlan(context: AutomationExplorer.Context) {
        (this as AutomationSpec.Explorer).createPlan().invoke(context)
    }

    @Test
    fun `a disabled archive button aborts the plan`() = runTest2 {
        val window = window("Archive", buttonEnabled = false)

        shouldThrow<ArchiveUnavailableException> {
            specs.getArchive(pkg).runPlan(contextFor(window))
        }

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 0) { window.button.performAction(any()) }
    }

    @Test
    fun `an enabled archive button is clicked`() = runTest2 {
        val window = window("Archive", buttonEnabled = true)

        specs.getArchive(pkg).runPlan(contextFor(window))

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 1) { window.button.performAction(ACSNodeInfo.ACTION_CLICK) }
    }

    @Test
    fun `a disabled restore button aborts the plan`() = runTest2 {
        val window = window("Restore", buttonEnabled = false)

        shouldThrow<RestoreUnavailableException> {
            specs.getRestore(pkg).runPlan(contextFor(window))
        }

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 0) { window.button.performAction(any()) }
    }

    @Test
    fun `an enabled restore button is clicked`() = runTest2 {
        val window = window("Restore", buttonEnabled = true)

        specs.getRestore(pkg).runPlan(contextFor(window))

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 1) { window.button.performAction(ACSNodeInfo.ACTION_CLICK) }
    }
}
