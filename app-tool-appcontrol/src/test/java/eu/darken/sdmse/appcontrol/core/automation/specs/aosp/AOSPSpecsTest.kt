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
import eu.darken.sdmse.common.BuildWrap
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.GeneralSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
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

    private class LabelOnlyWindow(
        val root: ACSNodeInfo,
        val label: ACSNodeInfo,
    )

    // root -> unclickable label, no clickable node anywhere: a disabled (or still loading) Archive action on Android 16
    private fun labelOnlyWindow(label: String): LabelOnlyWindow {
        val root = mockk<ACSNodeInfo>(relaxed = true)
        val labelNode = mockk<ACSNodeInfo>(relaxed = true).apply {
            every { text } returns label
            every { isClickable } returns false
            every { isEnabled } returns true
            every { parent } returns root
            every { childCount } returns 0
            every { getScreenBounds() } returns ACSNodeInfo.ScreenBounds(left = 120, top = 600, right = 300, bottom = 650)
            every { performAction(any()) } returns true
        }
        root.apply {
            every { text } returns null
            every { isClickable } returns false
            every { parent } returns null
            every { childCount } returns 1
            every { getChild(0) } returns labelNode
            every { getScreenBounds() } returns ACSNodeInfo.ScreenBounds(left = 0, top = 0, right = 1080, bottom = 2400)
        }
        return LabelOnlyWindow(root = root, label = labelNode)
    }

    private inline fun onApiLevel(level: Int, block: () -> Unit) {
        mockkObject(BuildWrap)
        mockkObject(BuildWrap.VERSION)
        try {
            every { BuildWrap.VERSION.SDK_INT } returns level
            every { BuildWrap.VERSION.CODENAME } returns "REL"
            block()
        } finally {
            unmockkObject(BuildWrap.VERSION)
            unmockkObject(BuildWrap)
        }
    }

    private fun contextFor(window: Window): AutomationExplorer.Context = contextFor(listOf(window.root))

    // The n-th windowRoot() call returns roots[n]; the last entry repeats for every later call.
    private fun contextFor(roots: List<ACSNodeInfo?>): AutomationExplorer.Context {
        var reads = 0
        val automationHost = mockk<AutomationHost>(relaxed = true).apply {
            coEvery { windowRoot() } coAnswers { roots[minOf(reads++, roots.lastIndex)] }
        }
        return mockk<AutomationExplorer.Context>(relaxed = true).apply {
            every { host } returns automationHost
        }
    }

    private suspend fun AutomationSpec.runPlan(context: AutomationExplorer.Context) {
        (this as AutomationSpec.Explorer).createPlan().invoke(context)
    }

    @Test
    fun `a disabled archive button aborts the plan after the settle window`() = runTest2 {
        val window = window("Archive", buttonEnabled = false)

        shouldThrow<ArchiveUnavailableException> {
            specs.getArchive(pkg).runPlan(contextFor(window))
        }

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 0) { window.button.performAction(any()) }
        currentTime shouldBeGreaterThanOrEqual 3000L
    }

    @Test
    fun `an enabled archive button is clicked`() = runTest2 {
        val window = window("Archive", buttonEnabled = true)

        specs.getArchive(pkg).runPlan(contextFor(window))

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 1) { window.button.performAction(ACSNodeInfo.ACTION_CLICK) }
        currentTime shouldBe 0L
    }

    @Test
    fun `an archive button that becomes enabled is clicked on its fresh node`() = runTest2 {
        val loading = window("Archive", buttonEnabled = false)
        val loaded = window("Archive", buttonEnabled = true)

        specs.getArchive(pkg).runPlan(contextFor(listOf(loading.root, loading.root, loaded.root)))

        nodeActionResults shouldContainExactly listOf(true)
        verify(exactly = 1) { loaded.button.performAction(ACSNodeInfo.ACTION_CLICK) }
        verify(exactly = 0) { loading.button.performAction(any()) }
    }

    @Test
    fun `an archive button that vanishes while settling retries the step`() = runTest2 {
        val loading = window("Archive", buttonEnabled = false)
        val other = window("Uninstall", buttonEnabled = true)

        specs.getArchive(pkg).runPlan(contextFor(listOf(loading.root, other.root)))

        nodeActionResults shouldContainExactly listOf(false)
        verify(exactly = 0) { loading.button.performAction(any()) }
        verify(exactly = 0) { other.button.performAction(any()) }
    }

    @Test
    fun `a missing window root while settling retries the step at the deadline`() = runTest2 {
        val loading = window("Archive", buttonEnabled = false)

        specs.getArchive(pkg).runPlan(contextFor(listOf(loading.root, null)))

        nodeActionResults shouldContainExactly listOf(false)
        verify(exactly = 0) { loading.button.performAction(any()) }
        currentTime shouldBeGreaterThanOrEqual 3000L
    }

    @Test
    fun `a label-only archive button on API 35 aborts the plan after the settle window`() = runTest2 {
        onApiLevel(35) {
            val window = labelOnlyWindow("Archive")

            shouldThrow<ArchiveUnavailableException> {
                specs.getArchive(pkg).runPlan(contextFor(listOf(window.root)))
            }

            nodeActionResults shouldContainExactly listOf(true)
            verify(exactly = 0) { window.label.performAction(any()) }
            currentTime shouldBeGreaterThanOrEqual 3000L
        }
    }

    @Test
    fun `a label-only archive button on API 35 that gains its button is clicked`() = runTest2 {
        onApiLevel(35) {
            val loading = labelOnlyWindow("Archive")
            val loaded = window("Archive", buttonEnabled = true)

            specs.getArchive(pkg).runPlan(contextFor(listOf(loading.root, loading.root, loaded.root)))

            nodeActionResults shouldContainExactly listOf(true)
            verify(exactly = 1) { loaded.button.performAction(ACSNodeInfo.ACTION_CLICK) }
            verify(exactly = 0) { loading.label.performAction(any()) }
        }
    }

    @Test
    fun `cancellation while settling propagates`() = runTest2 {
        val loading = window("Archive", buttonEnabled = false)
        var thrown: Throwable? = null

        val job = launch {
            try {
                specs.getArchive(pkg).runPlan(contextFor(loading))
            } catch (e: Throwable) {
                thrown = e
                throw e
            }
        }
        advanceTimeBy(1000)
        job.cancelAndJoin()

        job.isCancelled shouldBe true
        thrown.shouldBeInstanceOf<CancellationException>()
        nodeActionResults.shouldBeEmpty()
        verify(exactly = 0) { loading.button.performAction(any()) }
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
