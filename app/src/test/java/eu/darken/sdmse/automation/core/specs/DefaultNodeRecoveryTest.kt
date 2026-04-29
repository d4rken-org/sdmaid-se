package eu.darken.sdmse.automation.core.specs

import eu.darken.sdmse.automation.core.common.ACSNodeInfo
import eu.darken.sdmse.automation.core.common.stepper.StepContext
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.automation.TestAutomationHost

class DefaultNodeRecoveryTest : BaseTest() {

    private val testSpec = object : SpecGenerator {
        override val tag: String = "TestSpec"
        override suspend fun isResponsible(pkg: Installed): Boolean = true
    }

    private lateinit var testPkg: Installed
    private lateinit var testHost: TestAutomationHost
    private lateinit var stepContext: StepContext

    @BeforeEach
    fun setup() {
        testPkg = mockk {
            every { id } returns "test.pkg".toPkgId()
            every { packageName } returns "test.pkg"
            every { installId } returns mockk {
                every { pkgId } returns "test.pkg".toPkgId()
                every { userHandle } returns mockk<UserHandle2> { every { handleId } returns 0 }
            }
        }

        testHost = TestAutomationHost(kotlinx.coroutines.test.TestScope())

        val testContext = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress: Flow<Progress.Data?> = emptyFlow()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }

        stepContext = StepContext(
            hostContext = testContext,
            tag = "test",
            stepAttempts = 0,
        )
    }

    // ============================================================
    // Busy-node detection with textContainsAny
    // ============================================================

    @Test
    fun `busy-node detected for exact ellipsis text`() = runTest {
        val busyNode = TestACSNodeInfo(text = "…")
        val root = TestACSNodeInfo().addChild(busyNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
    }

    @Test
    fun `busy-node detected for exact three dots text`() = runTest {
        val busyNode = TestACSNodeInfo(text = "...")
        val root = TestACSNodeInfo().addChild(busyNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
    }

    @Test
    fun `busy-node detected for Computing with ellipsis`() = runTest {
        val busyNode = TestACSNodeInfo(text = "Computing\u2026")
        val root = TestACSNodeInfo().addChild(busyNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
    }

    @Test
    fun `busy-node detected for localized computing text with ellipsis`() = runTest {
        val busyNode = TestACSNodeInfo(text = "Berechnung\u2026")
        val root = TestACSNodeInfo().addChild(busyNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
    }

    @Test
    fun `busy-node not detected for long text containing ellipsis`() = runTest {
        val longText = "This is a very long description that happens to contain\u2026"
        val node = TestACSNodeInfo(text = longText)
        val scrollable = TestACSNodeInfo(isScrollable = true)
        val root = TestACSNodeInfo().addChildren(node, scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        // Should NOT trigger busy-node (text > 30 chars), should scroll instead
        result shouldBe true
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
    }

    @Test
    fun `busy-node not detected for text without ellipsis`() = runTest {
        val node = TestACSNodeInfo(text = "Storage & cache")
        val scrollable = TestACSNodeInfo(isScrollable = true)
        val root = TestACSNodeInfo().addChildren(node, scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        // Should scroll, not delay
        result shouldBe true
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
    }

    // ============================================================
    // Extra busy labels
    // ============================================================

    @Test
    fun `extraBusyLabels matches exact localized computing text`() = runTest {
        val busyNode = TestACSNodeInfo(text = "Berechnung\u2026")
        val root = TestACSNodeInfo().addChild(busyNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg, extraBusyLabels = setOf("Berechnung\u2026"))
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
    }

    @Test
    fun `extraBusyLabels does not match unrelated text`() = runTest {
        val node = TestACSNodeInfo(text = "Storage & cache")
        val scrollable = TestACSNodeInfo(isScrollable = true)
        val root = TestACSNodeInfo().addChildren(node, scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg, extraBusyLabels = setOf("Computing\u2026"))
        val result = recovery.invoke(stepContext, root)

        // Should scroll, not delay
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
    }

    // ============================================================
    // Scroll forward and backward
    // ============================================================

    @Test
    fun `scrolls forward when no busy-node found`() = runTest {
        val scrollable = TestACSNodeInfo(isScrollable = true)
        val root = TestACSNodeInfo().addChild(scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
        scrollable.performedActions shouldNotContain ACSNodeInfo.ACTION_SCROLL_BACKWARD
    }

    @Test
    fun `scrolls backward when forward scroll fails`() = runTest {
        // performActionResult=false means forward scroll will fail (at bottom of page)
        // but backward should also fail since it uses same performActionResult
        // We need a node that fails forward but succeeds backward
        // TestACSNodeInfo doesn't support per-action results, so let's test the sequence:
        // A node where performAction always returns false means both forward and backward fail
        val scrollable = TestACSNodeInfo(isScrollable = true, performActionResult = false)
        val root = TestACSNodeInfo().addChild(scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        // Both forward and backward fail, so recovery returns false
        result shouldBe false
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_BACKWARD
    }

    @Test
    fun `does not scroll backward when forward scroll succeeds`() = runTest {
        val scrollable = TestACSNodeInfo(isScrollable = true, performActionResult = true)
        val root = TestACSNodeInfo().addChild(scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
        scrollable.performedActions shouldNotContain ACSNodeInfo.ACTION_SCROLL_BACKWARD
    }

    @Test
    fun `returns false when no scrollable nodes and no busy-nodes`() = runTest {
        val textNode = TestACSNodeInfo(text = "Storage & cache")
        val root = TestACSNodeInfo().addChild(textNode)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe false
    }

    @Test
    fun `busy-node null text does not crash`() = runTest {
        val nullTextNode = TestACSNodeInfo(text = null)
        val scrollable = TestACSNodeInfo(isScrollable = true)
        val root = TestACSNodeInfo().addChildren(nullTextNode, scrollable)

        val recovery = testSpec.defaultNodeRecovery(testPkg)
        val result = recovery.invoke(stepContext, root)

        result shouldBe true
        scrollable.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
    }
}
