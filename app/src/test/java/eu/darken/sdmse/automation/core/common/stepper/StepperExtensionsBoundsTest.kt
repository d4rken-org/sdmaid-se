package eu.darken.sdmse.automation.core.common.stepper

import android.graphics.Rect
import eu.darken.sdmse.automation.core.specs.AutomationExplorer
import eu.darken.sdmse.common.progress.Progress
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
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

    // ============================================================
    // findColumnAlignedClickable - AOSP App-Info action row geometry
    // ============================================================

    /** Builds a StepContext whose host reports [root] as the window root (needed for the width cap). */
    private fun TestScope.contextWithRoot(root: TestACSNodeInfo): StepContext {
        val testHost = TestAutomationHost(this).apply { setWindowRoot(root) }
        val context = object : AutomationExplorer.Context {
            override val host get() = testHost
            override val progress = emptyFlow<Progress.Data?>()
            override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {}
        }
        return StepContext(hostContext = context, tag = "test", stepAttempts = 0)
    }

    // Real geometry from the reported AOSP action row (960px-wide window): Force stop label
    // center-X ≈ 776; the only clickable is the Uninstall cell at x[332,628].

    @Test
    fun `findColumnAlignedClickable returns null when only a neighbouring action is clickable`() = runTest {
        // Force stop disabled -> no clickable in its column; the nearest clickable is the Uninstall
        // cell, which must NOT be matched (that was the Uninstall-instead-of-force-stop bug).
        val root = TestACSNodeInfo(viewIdResourceName = "root", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 900))
        val uninstall = TestACSNodeInfo(viewIdResourceName = "uninstall", isClickable = true, bounds = Rect(332, 649, 628, 828))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 906, 873))
        row.addChildren(uninstall, fsLabel)
        root.addChild(row)

        val context = contextWithRoot(root)

        context.findColumnAlignedClickable(fsLabel) shouldBe null
    }

    @Test
    fun `findColumnAlignedClickable returns the same-column clickable, not the neighbour`() = runTest {
        // Force stop enabled -> its own clickable wrapper sits in the label's column.
        val root = TestACSNodeInfo(viewIdResourceName = "root", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 900))
        val uninstall = TestACSNodeInfo(viewIdResourceName = "uninstall", isClickable = true, bounds = Rect(332, 649, 628, 828))
        val fsClickable = TestACSNodeInfo(viewIdResourceName = "fs_clickable", isClickable = true, bounds = Rect(646, 649, 906, 828))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 906, 873))
        row.addChildren(uninstall, fsClickable, fsLabel)
        root.addChild(row)

        val context = contextWithRoot(root)

        context.findColumnAlignedClickable(fsLabel) shouldBeSameInstanceAs fsClickable
    }

    @Test
    fun `findColumnAlignedClickable matches a clickable stacked directly above the label`() = runTest {
        // Android 16 "button over text" layout: the clickable Button sits above its non-overlapping
        // label TextView in the same column (real shape from the clear-cache action row).
        val root = TestACSNodeInfo(viewIdResourceName = "root", bounds = Rect(0, 0, 1080, 2400))
        val action = TestACSNodeInfo(viewIdResourceName = "action", bounds = Rect(540, 900, 1020, 1240))
        val button = TestACSNodeInfo(viewIdResourceName = "button", isClickable = true, bounds = Rect(691, 959, 869, 1098))
        val label = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "label", bounds = Rect(628, 1113, 931, 1181))
        action.addChildren(button, label)
        root.addChild(action)

        val context = contextWithRoot(root)

        context.findColumnAlignedClickable(label) shouldBeSameInstanceAs button
    }

    @Test
    fun `findColumnAlignedClickable rejects a full-width clickable row below the action grid`() = runTest {
        // A full-width settings row below the grid horizontally contains the label center but does
        // not vertically overlap it (and spans multiple columns) -> must be rejected.
        val root = TestACSNodeInfo(viewIdResourceName = "root", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 1100))
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 906, 873))
        val notifRow = TestACSNodeInfo(viewIdResourceName = "notif", isClickable = true, bounds = Rect(36, 907, 924, 1069))
        row.addChildren(fsLabel, notifRow)
        root.addChild(row)

        val context = contextWithRoot(root)

        context.findColumnAlignedClickable(fsLabel) shouldBe null
    }

    @Test
    fun `findColumnAlignedClickable returns null for empty label bounds`() = runTest {
        val root = TestACSNodeInfo(viewIdResourceName = "root", bounds = Rect(0, 0, 960, 2142))
        val row = TestACSNodeInfo(viewIdResourceName = "row", bounds = Rect(0, 600, 960, 900))
        val uninstall = TestACSNodeInfo(viewIdResourceName = "uninstall", isClickable = true, bounds = Rect(332, 649, 628, 828))
        // Degenerate/clipped label (left >= right): geometry is meaningless -> no match.
        val fsLabel = TestACSNodeInfo(text = "Force stop", viewIdResourceName = "fs_label", bounds = Rect(646, 789, 646, 789))
        row.addChildren(uninstall, fsLabel)
        root.addChild(row)

        val context = contextWithRoot(root)

        context.findColumnAlignedClickable(fsLabel) shouldBe null
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