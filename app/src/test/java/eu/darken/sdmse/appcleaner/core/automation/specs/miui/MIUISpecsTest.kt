package eu.darken.sdmse.appcleaner.core.automation.specs.miui

import android.content.Context
import eu.darken.sdmse.appcleaner.core.automation.specs.BaseAppCleanerSpecTest
import eu.darken.sdmse.appcleaner.core.automation.specs.aosp.AOSPLabels
import eu.darken.sdmse.automation.core.AutomationHost
import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.crawl
import eu.darken.sdmse.automation.core.common.stepper.AutomationStep
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.automation.core.errors.PlanAbortException
import eu.darken.sdmse.automation.core.errors.StepAbortException
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.automation.core.specs.AutomationSpec
import eu.darken.sdmse.automation.core.specs.getLocales
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.deviceadmin.DeviceAdminManager
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.Progress
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.TestACSNodeInfo
import java.util.Locale

class MIUISpecsTest : BaseAppCleanerSpecTest<MIUISpecs, MIUILabels>() {

    override val romType = RomType.MIUI

    private lateinit var context: Context
    private lateinit var aospLabels: AOSPLabels
    private lateinit var deviceAdminManager: DeviceAdminManager

    override fun createLabels(): MIUILabels {
        context = mockk(relaxed = true)
        aospLabels = mockk()
        deviceAdminManager = mockk()
        coEvery { deviceAdminManager.getDeviceAdmins() } returns emptySet()
        return mockk()
    }

    override fun createSpec() = MIUISpecs(
        context = context,
        ipcFunnel = ipcFunnel,
        deviceDetective = deviceDetective,
        miuiLabels = labels,
        aospLabels = aospLabels,
        deviceAdminManager = deviceAdminManager,
        storageEntryFinder = storageEntryFinder,
        romTypeProvider = romTypeProvider,
        stepper = stepper,
    )

    override fun mockLabelDefaults() {
        every { labels.getClearDataButtonLabels(any()) } returns setOf("Clear data")
        every { labels.getClearAllDataButtonLabels(any()) } returns setOf("Clear all data")
        every { labels.getClearCacheButtonLabels(any()) } returns setOf("Clear cache")
        every { labels.getDialogTitles(any()) } returns setOf("Clear cache?")

        coEvery { ipcFunnel.use<Any?>(any()) } returns null
    }

    @AfterEach
    fun cleanupStatics() {
        unmockkStatic("eu.darken.sdmse.automation.core.specs.AutomationExplorerExtensionsKt")
    }

    private class PlanRun {
        val processed = mutableListOf<String>()
        var clearDataResult: Boolean? = null
        var stepAbort: StepAbortException? = null
    }

    private fun contextFor(host: AutomationHost) = object : AutomationExplorer.Context {
        override val host: AutomationHost = host
        override val progress: Flow<Progress.Data?> = emptyFlow()
        override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
    }

    private fun appInfoTree(vararg buttons: String): TestACSNodeInfo = TestACSNodeInfo(
        packageName = SECURITY_CENTER,
    ).addChildren(
        TestACSNodeInfo(text = PKG_NAME, packageName = SECURITY_CENTER),
        *buttons.map {
            TestACSNodeInfo(
                text = it,
                packageName = SECURITY_CENTER,
                viewIdResourceName = "com.miui.securitycenter:id/action_menu_item_child_text",
                isClickable = true,
            )
        }.toTypedArray(),
    )

    private fun TestACSNodeInfo.allNodes(): List<TestACSNodeInfo> =
        crawl().map { it.node as TestACSNodeInfo }.toList()

    private fun TestACSNodeInfo.node(text: String): TestACSNodeInfo = allNodes().single { it.text == text }

    private suspend fun runPlan(clearDataHost: AutomationHost = testHost): PlanRun {
        val run = PlanRun()
        val pkg: Installed = createTestPkg(PKG_NAME).also {
            every { it.label } returns null
        }

        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            run.processed.add(step.descriptionInternal)
            when {
                step.descriptionInternal.startsWith("Storage entry (main plan)") -> {
                    step.windowCheck!!(StepContext(hostContext = testContext, tag = "test", stepAttempts = 0))
                }

                step.descriptionInternal.startsWith("Clear data (security center plan)") -> {
                    val stepContext = StepContext(
                        hostContext = contextFor(clearDataHost),
                        tag = "test",
                        stepAttempts = 0,
                    )
                    try {
                        run.clearDataResult = step.nodeAction!!(stepContext)
                    } catch (e: StepAbortException) {
                        run.stepAbort = e
                    }
                }
            }
            Unit
        }

        val plan = (createSpec().getClearCache(pkg) as AutomationSpec.Explorer).createPlan()
        plan.invoke(testContext)
        return run
    }

    @Test
    fun `clear all data only - aborts the plan as failure without clicking`() = runTest {
        val tree = appInfoTree("Force stop", "Uninstall", "Clear all data")
        testHost.setWindowRoot(tree)

        val error = shouldThrow<PlanAbortException> {
            runPlan()
        }

        error.treatAsSuccess shouldBe false
        tree.allNodes().forEach { it.performedActions.shouldBeEmpty() }
    }

    @Test
    fun `clear all data abort happens on the first action invocation`() = runTest {
        testHost.setWindowRoot(appInfoTree("Clear all data"))

        var invocations = 0
        coEvery { stepper.process(any(), any()) } coAnswers {
            val step = secondArg<AutomationStep>()
            val stepContext = StepContext(hostContext = testContext, tag = "test", stepAttempts = 0)
            when {
                step.descriptionInternal.startsWith("Storage entry (main plan)") -> step.windowCheck!!(stepContext)
                step.descriptionInternal.startsWith("Clear data (security center plan)") -> {
                    invocations++
                    step.nodeAction!!(stepContext)
                }
            }
            Unit
        }
        val pkg = createTestPkg(PKG_NAME).also { every { it.label } returns null }
        val plan = (createSpec().getClearCache(pkg) as AutomationSpec.Explorer).createPlan()

        shouldThrow<PlanAbortException> { plan.invoke(testContext) }
        invocations shouldBe 1
    }

    @Test
    fun `clear data next to clear all data - clicks clear data`() = runTest {
        val tree = appInfoTree("Force stop", "Clear data", "Clear all data")
        testHost.setWindowRoot(tree)

        val run = runPlan()

        run.clearDataResult shouldBe true
        run.stepAbort.shouldBeNull()
        tree.node("Clear data").performedActions shouldContainExactly listOf(ACSNodeInfo.ACTION_CLICK)
        tree.node("Clear all data").performedActions.shouldBeEmpty()
    }

    @Test
    fun `clear cache next to clear all data - skips to the alternative step`() = runTest {
        val tree = appInfoTree("Force stop", "Clear cache", "Clear all data")
        testHost.setWindowRoot(tree)

        val run = runPlan()

        run.stepAbort.shouldNotBeNull().treatAsSuccess shouldBe true
        run.clearDataResult.shouldBeNull()
        run.processed.any { it.startsWith("Clear cache (alternative security center plan)") } shouldBe true
        tree.node("Clear all data").performedActions.shouldBeEmpty()
    }

    @Test
    fun `no clear labels at all - keeps polling without abort`() = runTest {
        val tree = appInfoTree("Force stop", "Uninstall")
        testHost.setWindowRoot(tree)

        val run = runPlan()

        run.clearDataResult shouldBe false
        run.stepAbort.shouldBeNull()
        tree.allNodes().forEach { it.performedActions.shouldBeEmpty() }
    }

    private fun changingHost(early: TestACSNodeInfo, late: TestACSNodeInfo): AutomationHost {
        var reads = 0
        // The action reads the root for the clear-cache lookup, then the clear-data lookup, then the abort check.
        return object : AutomationHost by testHost {
            override suspend fun windowRoot(): ACSNodeInfo = if (++reads <= 2) early else late
        }
    }

    @Test
    fun `screen gains clear data after the earlier lookups - no abort`() = runTest {
        testHost.setWindowRoot(appInfoTree())
        val early = appInfoTree("Clear all data")
        val late = appInfoTree("Clear data", "Clear all data")

        val run = runPlan(clearDataHost = changingHost(early, late))

        run.clearDataResult shouldBe false
        run.stepAbort.shouldBeNull()
        late.allNodes().forEach { it.performedActions.shouldBeEmpty() }
    }

    @Test
    fun `screen gains clear cache after the earlier lookups - no abort`() = runTest {
        testHost.setWindowRoot(appInfoTree())
        val early = appInfoTree("Clear all data")
        val late = appInfoTree("Clear cache", "Clear all data")

        val run = runPlan(clearDataHost = changingHost(early, late))

        run.clearDataResult shouldBe false
        run.stepAbort.shouldBeNull()
        late.allNodes().forEach { it.performedActions.shouldBeEmpty() }
    }

    private fun realLabels(vararg locales: String, dynamic: String? = null): Pair<MIUILabels, AutomationExplorer.Context> {
        mockkStatic("eu.darken.sdmse.automation.core.specs.AutomationExplorerExtensionsKt")
        every { any<AutomationExplorer.Context>().getLocales() } returns locales.map { Locale.forLanguageTag(it) }

        val miuiLabels = spyk(MIUILabels())
        every { with(miuiLabels) { any<Context>().get3rdPartyString(any(), any(), any()) } } returns null
        if (dynamic != null) {
            every {
                with(miuiLabels) { any<Context>().get3rdPartyString(any(), "app_manager_clear_all_data", any()) }
            } returns dynamic
        }
        return miuiLabels to testContext
    }

    @Test
    fun `clear all data labels - pt-BR fallback`() {
        val (miuiLabels, ctx) = realLabels("pt-BR")
        miuiLabels.getClearAllDataButtonLabels(ctx) shouldBe setOf("Limpar todos os dados")
    }

    @Test
    fun `clear all data labels - english fallback`() {
        val (miuiLabels, ctx) = realLabels("en-US")
        miuiLabels.getClearAllDataButtonLabels(ctx) shouldBe setOf("Clear all data")
    }

    @Test
    fun `clear all data labels - unmapped locale has no fallback`() {
        val (miuiLabels, ctx) = realLabels("de-DE")
        miuiLabels.getClearAllDataButtonLabels(ctx).shouldBeEmpty()
    }

    @Test
    fun `clear all data labels - includes the dynamic security center string`() {
        val (miuiLabels, ctx) = realLabels("pt-BR", dynamic = "Limpar todos os dados (dyn)")
        val result = miuiLabels.getClearAllDataButtonLabels(ctx)
        result shouldContain "Limpar todos os dados (dyn)"
        result shouldContain "Limpar todos os dados"
    }

    companion object {
        private const val PKG_NAME = "bitpit.launcher"
        private const val SECURITY_CENTER = "com.miui.securitycenter"
    }
}
