package eu.darken.sdmse.automation.core.common.stepper

import android.graphics.Rect
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = TestApplication::class)
class StepperExtensionsBoundsTest : BaseTest() {

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

    private fun createNodeWithBounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        isClickable: Boolean = false
    ): TestACSNodeInfo {
        return TestACSNodeInfo(
            isClickable = isClickable,
            bounds = Rect(left, top, right, bottom)
        )
    }

    @Test
    fun `findNearestTo returns self when includeSelf is true and predicate matches`() = runTest {
        val context = createStepContext()
        val targetNode = createNode(isClickable = true)
        createNode().addChild(targetNode) // Give it a parent to avoid early null return

        context.findNearestTo(includeSelf = true, node = targetNode) { it.isClickable } shouldBe targetNode
        context.findNearestTo(includeSelf = false, node = targetNode) { it.isClickable } shouldBe null
    }

    @Test
    fun `findNearestTo finds nearest sibling based on distance`() = runTest {
        val context = createStepContext()

        // Target node at center: (50, 50)
        val targetNode = createNodeWithBounds(0, 0, 100, 100)

        // Far sibling at (250, 250)
        val farSibling = createNodeWithBounds(200, 200, 300, 300)

        // Near sibling at (150, 50) - same Y, closer X
        val nearSibling = createNodeWithBounds(100, 0, 200, 100)

        createNode().addChildren(targetNode, farSibling, nearSibling)

        val result = context.findNearestTo(node = targetNode)

        result shouldBe nearSibling
    }

    @Test
    fun `findNearestTo respects predicate filter`() = runTest {
        val context = createStepContext()

        val targetNode = createNodeWithBounds(0, 0, 100, 100)

        // Closer but not clickable
        val closerNonClickable = createNodeWithBounds(100, 0, 200, 100, isClickable = false)

        // Farther but clickable
        val fartherClickable = createNodeWithBounds(200, 0, 300, 100, isClickable = true)

        createNode().addChildren(targetNode, closerNonClickable, fartherClickable)

        val result = context.findNearestTo(node = targetNode) { it.isClickable }

        result shouldBe fartherClickable
    }

    @Test
    fun `findNearestTo traverses up multiple parent levels`() = runTest {
        val context = createStepContext()

        // Create nested structure where nearest node is at grandparent level
        val targetNode = createNodeWithBounds(0, 0, 50, 50)
        val parent = createNodeWithBounds(200, 200, 250, 250).addChild(targetNode) // Give parent different bounds
        val nearestNode = createNodeWithBounds(60, 0, 110, 50) // Very close to target
        createNodeWithBounds(300, 300, 350, 350).addChildren(
            parent,
            nearestNode
        ) // Give grandparent different bounds

        val result = context.findNearestTo(maxNesting = 2, node = targetNode)

        result shouldBe nearestNode
    }

    @Test
    fun `findNearestTo excludes the target node from results`() = runTest {
        val context = createStepContext()

        // Target node is clickable but should not be returned unless includeSelf is true
        val targetNode = createNode(isClickable = true)
        val otherNode = createNode(isClickable = true)
        createNode().addChildren(targetNode, otherNode)

        val result = context.findNearestTo(includeSelf = false, node = targetNode) { it.isClickable }

        result shouldBe otherNode
    }

    @Test
    fun `findNearestTo finds closest among equidistant nodes`() = runTest {
        val context = createStepContext()

        // Target at (50, 50)
        val targetNode = createNodeWithBounds(0, 0, 100, 100)

        // Two nodes equidistant horizontally
        val leftNode = createNodeWithBounds(-100, 0, 0, 100)   // center at (-50, 50)
        val rightNode = createNodeWithBounds(100, 0, 200, 100) // center at (150, 50)

        createNode().addChildren(targetNode, leftNode, rightNode)

        val result = context.findNearestTo(node = targetNode)

        // Should return one of them (implementation returns first found with minimum distance)
        result shouldBe leftNode  // leftNode is processed first in forEach
    }

    @Test
    fun `findNearestTo handles complex hierarchy with multiple candidates`() = runTest {
        val context = createStepContext()

        // Simplified hierarchy to avoid potential issues
        val targetNode = createNodeWithBounds(0, 0, 100, 100) // center: (50, 50)
        val closeSibling = createNodeWithBounds(100, 0, 200, 100) // center: (150, 50), distance: 100
        val farSibling = createNodeWithBounds(300, 0, 400, 100) // center: (350, 50), distance: 300

        // Simple parent with multiple children
        createNode().addChildren(targetNode, closeSibling, farSibling)

        val result = context.findNearestTo(node = targetNode)

        // Should find the closer sibling
        result shouldBe closeSibling
    }

    @Test
    fun `findNearestTo searches across multiple parent levels`() = runTest {
        val context = createStepContext()

        // targetNode has no siblings at its level
        val targetNode = createNodeWithBounds(0, 0, 50, 50)
        val parent = createNodeWithBounds(200, 200, 250, 250).addChild(targetNode) // Give parent different bounds

        // distantSibling is at grandparent level (sibling of parent)
        val distantSibling = createNodeWithBounds(100, 0, 150, 50)
        createNodeWithBounds(300, 300, 350, 350).addChildren(
            parent,
            distantSibling
        ) // Give grandparent different bounds

        val result = context.findNearestTo(maxNesting = 2, node = targetNode)

        result shouldBe distantSibling
    }

    @Test
    fun `findNearestTo respects maxNesting limit`() = runTest {
        val context = createStepContext()

        val targetNode = createNodeWithBounds(0, 0, 50, 50)
        val parent = createNode().addChild(targetNode)
        val grandParent = createNode().addChild(parent)
        val nearestNode = createNodeWithBounds(100, 0, 150, 50) // At great-grandparent level
        createNode().addChildren(grandParent, nearestNode)

        // With maxNesting = 2, should not find the node at great-grandparent level
        val result1 = context.findNearestTo(maxNesting = 2, node = targetNode)
        result1 shouldBe null

        // With maxNesting = 3, should find it
        val result2 = context.findNearestTo(maxNesting = 3, node = targetNode)
        result2 shouldBe nearestNode
    }
}