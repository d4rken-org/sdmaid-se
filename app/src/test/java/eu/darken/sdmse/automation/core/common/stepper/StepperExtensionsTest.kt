package eu.darken.sdmse.automation.core.common.stepper

import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo

class StepperExtensionsTest : BaseTest() {

    private fun createStepContext(): StepContext {
        val mockHostContext = mockk<AutomationExplorer.Context>()
        return StepContext(
            hostContext = mockHostContext,
            tag = "test",
            stepAttempts = 1
        )
    }

    private fun createNode(isClickable: Boolean = false): TestACSNodeInfo {
        return TestACSNodeInfo(isClickable = isClickable)
    }

    @Test
    fun `findClickableSibling returns null when node has no parent`() = runTest {
        val context = createStepContext()
        val orphanNode = createNode()

        context.findClickableSibling(node = orphanNode) shouldBe null
    }

    @Test
    fun `findClickableSibling returns self when includeSelf is true and node is clickable`() = runTest {
        val context = createStepContext()
        val targetNode = createNode(isClickable = true)

        context.findClickableSibling(includeSelf = true, node = targetNode) shouldBe targetNode
        context.findClickableSibling(includeSelf = false, node = targetNode) shouldBe null
    }

    @Test
    fun `findClickableSibling basic functionality test`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val clickableSibling = createNode(isClickable = true)
        createNode().addChildren(targetNode, clickableSibling)

        val result = context.findClickableSibling(node = targetNode)

        result shouldBe clickableSibling
    }

    @Test
    fun `findClickableSibling skips non-clickable siblings`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val nonClickableSibling1 = createNode(isClickable = false)
        val clickableSibling = createNode(isClickable = true)
        val nonClickableSibling2 = createNode(isClickable = false)
        createNode().addChildren(targetNode, nonClickableSibling1, clickableSibling, nonClickableSibling2)

        val result = context.findClickableSibling(node = targetNode)

        result shouldBe clickableSibling
    }

    @Test
    fun `findClickableSibling traverses up multiple levels`() = runTest {
        val context = createStepContext()

        // Create nested structure: grandparent -> parent -> targetNode, clickableSibling
        val targetNode = createNode()
        val parent = createNode().addChild(targetNode)
        val clickableSibling = createNode(isClickable = true)
        createNode().addChildren(parent, clickableSibling)

        val result = context.findClickableSibling(maxNesting = 2, node = targetNode)

        result shouldBe clickableSibling
    }

    // Tests for findClickableParent
    @Test
    fun `findClickableParent returns null when node has no parent`() = runTest {
        val context = createStepContext()
        val orphanNode = createNode()

        context.findClickableParent(node = orphanNode) shouldBe null
    }

    @Test
    fun `findClickableParent returns self when includeSelf is true and node is clickable`() = runTest {
        val context = createStepContext()
        val targetNode = createNode(isClickable = true)

        context.findClickableParent(includeSelf = true, node = targetNode) shouldBe targetNode
        context.findClickableParent(includeSelf = false, node = targetNode) shouldBe null
    }

    @Test
    fun `findClickableParent finds immediate clickable parent`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val clickableParent = createNode(isClickable = true).addChild(targetNode)

        val result = context.findClickableParent(node = targetNode)

        result shouldBe clickableParent
    }

    @Test
    fun `findClickableParent skips non-clickable parents and finds clickable ancestor`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val nonClickableParent = createNode(isClickable = false).addChild(targetNode)
        val clickableGrandParent = createNode(isClickable = true).addChild(nonClickableParent)

        val result = context.findClickableParent(node = targetNode)

        result shouldBe clickableGrandParent
    }

    @Test
    fun `findClickableParent respects maxNesting limit`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val parent1 = createNode(isClickable = false).addChild(targetNode)
        val parent2 = createNode(isClickable = false).addChild(parent1)
        val parent3 = createNode(isClickable = true).addChild(parent2) // This should be found
        createNode(isClickable = true).addChild(parent3) // This is beyond maxNesting

        // With maxNesting = 3, should find parent3
        val result1 = context.findClickableParent(maxNesting = 3, node = targetNode)
        result1 shouldBe parent3

        // With maxNesting = 2, should not find any clickable parent
        val result2 = context.findClickableParent(maxNesting = 2, node = targetNode)
        result2 shouldBe null
    }

    @Test
    fun `findClickableParent returns first clickable parent in hierarchy`() = runTest {
        val context = createStepContext()

        val targetNode = createNode()
        val clickableParent = createNode(isClickable = true).addChild(targetNode)
        createNode(isClickable = true).addChild(clickableParent)

        val result = context.findClickableParent(node = targetNode)

        // Should return the first (immediate) clickable parent, not the grandparent
        result shouldBe clickableParent
    }
}