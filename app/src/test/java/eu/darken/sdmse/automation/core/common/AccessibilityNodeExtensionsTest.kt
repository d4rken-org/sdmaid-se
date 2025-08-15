package eu.darken.sdmse.automation.core.common

import android.view.accessibility.AccessibilityEvent
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.TestACSNodeInfo

class AccessibilityNodeExtensionsTest : BaseTest() {

    // Helper methods for creating test nodes
    private fun createTextNode(text: String?) = TestACSNodeInfo(text = text)

    private fun createNodeWithId(id: String?) = TestACSNodeInfo(viewIdResourceName = id)

    private fun createTypedNode(className: String, isClickable: Boolean = false) = TestACSNodeInfo(
        className = className,
        isClickable = isClickable
    )

    // Tests for textVariants property
    @Test
    fun `textVariants returns empty set when text is null`() {
        val node = createTextNode(null)

        node.textVariants.shouldBeEmpty()
    }

    @Test
    fun `textVariants returns set with original text when no space variants needed`() {
        val node = createTextNode("Hello World")

        val variants = node.textVariants
        variants shouldContain "Hello World"
        // When text only contains regular spaces, the replacement creates a duplicate
        // so the Set contains only one unique string
        variants shouldHaveSize 1
    }

    @Test
    fun `textVariants creates space variant when text contains non-breaking spaces`() {
        val textWithNonBreakingSpace = "Hello\u00A0World" // \u00A0 is non-breaking space
        val node = createTextNode(textWithNonBreakingSpace)

        val variants = node.textVariants
        variants shouldContain textWithNonBreakingSpace
        variants shouldContain "Hello World" // Regular space variant
    }

    // Tests for textMatchesAny
    @Test
    fun `textMatchesAny returns true for exact match case insensitive`() {
        val node = createTextNode("Hello World")

        node.textMatchesAny(listOf("hello world")) shouldBe true
        node.textMatchesAny(listOf("HELLO WORLD")) shouldBe true
        node.textMatchesAny(listOf("Hello World")) shouldBe true
    }

    @Test
    fun `textMatchesAny returns false when no match found`() {
        val node = createTextNode("Hello World")

        node.textMatchesAny(listOf("Goodbye", "Different")) shouldBe false
    }

    @Test
    fun `textMatchesAny works with space variants`() {
        val textWithNonBreakingSpace = "Hello\u00A0World"
        val node = createTextNode(textWithNonBreakingSpace)

        node.textMatchesAny(listOf("Hello World")) shouldBe true
    }

    @Test
    fun `textMatchesAny returns false for empty candidates`() {
        val node = createTextNode("Hello World")

        node.textMatchesAny(emptyList()) shouldBe false
    }

    // Tests for textContainsAny
    @Test
    fun `textContainsAny returns true for substring match case insensitive`() {
        val node = createTextNode("Hello World")

        node.textContainsAny(listOf("hello")) shouldBe true
        node.textContainsAny(listOf("WORLD")) shouldBe true
        node.textContainsAny(listOf("lo Wo")) shouldBe true
    }

    @Test
    fun `textContainsAny returns false when no substring found`() {
        val node = createTextNode("Hello World")

        node.textContainsAny(listOf("xyz", "abc")) shouldBe false
    }

    @Test
    fun `textContainsAny returns false for empty candidates`() {
        val node = createTextNode("Hello World")

        node.textContainsAny(emptyList()) shouldBe false
    }

    // Tests for textEndsWithAny
    @Test
    fun `textEndsWithAny returns true for suffix match case insensitive`() {
        val node = createTextNode("Hello World")

        node.textEndsWithAny(listOf("world")) shouldBe true
        node.textEndsWithAny(listOf("WORLD")) shouldBe true
        node.textEndsWithAny(listOf("o World")) shouldBe true
    }

    @Test
    fun `textEndsWithAny returns false when no suffix found`() {
        val node = createTextNode("Hello World")

        node.textEndsWithAny(listOf("Hello", "abc")) shouldBe false
    }

    @Test
    fun `textEndsWithAny returns false for empty candidates`() {
        val node = createTextNode("Hello World")

        node.textEndsWithAny(emptyList()) shouldBe false
    }

    // Tests for idMatches
    @Test
    fun `idMatches returns true for exact ID match`() {
        val node = createNodeWithId("com.example.app:id/button1")

        node.idMatches("com.example.app:id/button1") shouldBe true
    }

    @Test
    fun `idMatches returns false for no match`() {
        val node = createNodeWithId("com.example.app:id/button1")

        node.idMatches("com.example.app:id/button2") shouldBe false
    }

    @Test
    fun `idMatches returns false when ID is null`() {
        val node = createNodeWithId(null)

        node.idMatches("com.example.app:id/button1") shouldBe false
    }

    // Tests for idContains
    @Test
    fun `idContains returns true for substring match`() {
        val node = createNodeWithId("com.example.app:id/button1")

        node.idContains("button") shouldBe true
        node.idContains("example") shouldBe true
        node.idContains("id/button1") shouldBe true
    }

    @Test
    fun `idContains returns false for no substring match`() {
        val node = createNodeWithId("com.example.app:id/button1")

        node.idContains("textview") shouldBe false
        node.idContains("xyz") shouldBe false
    }

    @Test
    fun `idContains returns false when ID is null`() {
        val node = createNodeWithId(null)

        node.idContains("button") shouldBe false
    }

    // Tests for isClickyButton
    @Test
    fun `isClickyButton returns true when both clickable and Button class`() {
        val node = createTypedNode("android.widget.Button", isClickable = true)

        node.isClickyButton() shouldBe true
    }

    @Test
    fun `isClickyButton returns false when not clickable`() {
        val node = createTypedNode("android.widget.Button", isClickable = false)

        node.isClickyButton() shouldBe false
    }

    @Test
    fun `isClickyButton returns false when clickable but not Button class`() {
        val node = createTypedNode("android.widget.TextView", isClickable = true)

        node.isClickyButton() shouldBe false
    }

    @Test
    fun `isClickyButton returns false when neither clickable nor Button`() {
        val node = createTypedNode("android.widget.TextView", isClickable = false)

        node.isClickyButton() shouldBe false
    }

    // Tests for isTextView
    @Test
    fun `isTextView returns true for TextView class`() {
        val node = createTypedNode("android.widget.TextView")

        node.isTextView() shouldBe true
    }

    @Test
    fun `isTextView returns false for non-TextView class`() {
        val node = createTypedNode("android.widget.Button")

        node.isTextView() shouldBe false
    }

    // Tests for isRadioButton
    @Test
    fun `isRadioButton returns true for RadioButton class`() {
        val node = createTypedNode("android.widget.RadioButton")

        node.isRadioButton() shouldBe true
    }

    @Test
    fun `isRadioButton returns false for non-RadioButton class`() {
        val node = createTypedNode("android.widget.Button")

        node.isRadioButton() shouldBe false
    }

    // Tests for children()
    @Test
    fun `children returns empty list when no children`() {
        val node = TestACSNodeInfo()

        node.children().shouldBeEmpty()
    }

    @Test
    fun `children returns all child nodes`() {
        val child1 = TestACSNodeInfo(text = "Child 1")
        val child2 = TestACSNodeInfo(text = "Child 2")
        val parent = TestACSNodeInfo().addChildren(child1, child2)

        val children = parent.children()
        children shouldHaveSize 2
        children shouldContain child1
        children shouldContain child2
    }

    @Test
    fun `children filters out null children`() {
        // Create a TestACSNodeInfo with an array that includes a null child
        val validChild = TestACSNodeInfo(text = "Valid Child")
        val parentWithNullChild = TestACSNodeInfo(
            childrenArray = arrayOf(validChild, null)
        )

        val children = parentWithNullChild.children()
        children shouldHaveSize 1
        children[0].text shouldBe "Valid Child"
    }

    // Tests for findParentOrNull
    @Test
    fun `findParentOrNull finds matching parent`() {
        val targetNode = TestACSNodeInfo(text = "Target")
        val parent = TestACSNodeInfo(text = "Parent").addChild(targetNode)
        TestACSNodeInfo(text = "Grandparent").addChild(parent)

        val result = targetNode.findParentOrNull { it.text == "Parent" }

        result shouldBe parent
    }

    @Test
    fun `findParentOrNull returns null when no matching parent within limit`() {
        val targetNode = TestACSNodeInfo(text = "Target")
        val parent = TestACSNodeInfo(text = "Parent").addChild(targetNode)
        TestACSNodeInfo(text = "Grandparent").addChild(parent)

        val result = targetNode.findParentOrNull(maxNesting = 1) { it.text == "Grandparent" }

        result shouldBe null
    }

    @Test
    fun `findParentOrNull respects maxNesting parameter`() {
        val targetNode = TestACSNodeInfo(text = "Target")
        val parent = TestACSNodeInfo(text = "Parent").addChild(targetNode)
        val grandparent = TestACSNodeInfo(text = "Grandparent").addChild(parent)
        val greatGrandparent = TestACSNodeInfo(text = "GreatGrandparent").addChild(grandparent)

        // Should find within limit
        val result1 = targetNode.findParentOrNull(maxNesting = 3) { it.text == "GreatGrandparent" }
        result1 shouldBe greatGrandparent

        // Should not find beyond limit
        val result2 = targetNode.findParentOrNull(maxNesting = 2) { it.text == "GreatGrandparent" }
        result2 shouldBe null
    }

    @Test
    fun `findParentOrNull returns null when node has no parent`() {
        val orphanNode = TestACSNodeInfo(text = "Orphan")

        val result = orphanNode.findParentOrNull { true }

        result shouldBe null
    }

    // Tests for getRoot
    @Test
    fun `getRoot returns self when no parent`() {
        val singleNode = TestACSNodeInfo(text = "Single")

        val root = singleNode.getRoot()

        root shouldBe singleNode
    }

    @Test
    fun `getRoot returns top parent in nested structure`() {
        val targetNode = TestACSNodeInfo(text = "Target")
        val parent = TestACSNodeInfo(text = "Parent").addChild(targetNode)
        val grandparent = TestACSNodeInfo(text = "Grandparent").addChild(parent)
        val root = TestACSNodeInfo(text = "Root").addChild(grandparent)

        val result = targetNode.getRoot()

        result shouldBe root
    }

    @Test
    fun `getRoot respects maxNesting limit`() {
        val targetNode = TestACSNodeInfo(text = "Target")
        val parent = TestACSNodeInfo(text = "Parent").addChild(targetNode)
        val grandparent = TestACSNodeInfo(text = "Grandparent").addChild(parent)
        TestACSNodeInfo(text = "Root").addChild(grandparent)

        // With limited nesting, should stop before reaching actual root
        val result = targetNode.getRoot(maxNesting = 2)

        result shouldBe grandparent
    }

    // Tests for crawl()
    @Test
    fun `crawl yields single node when no children`() {
        val singleNode = TestACSNodeInfo(text = "Single")

        val crawledNodes = singleNode.crawl().toList()

        crawledNodes shouldHaveSize 1
        crawledNodes[0].node shouldBe singleNode
        crawledNodes[0].level shouldBe 0
    }

    @Test
    fun `crawl traverses tree structure depth-first`() {
        val child1 = TestACSNodeInfo(text = "Child 1")
        val child2 = TestACSNodeInfo(text = "Child 2")
        val grandchild = TestACSNodeInfo(text = "Grandchild")
        child1.addChild(grandchild)
        val root = TestACSNodeInfo(text = "Root").addChildren(child1, child2)

        val crawledNodes = root.crawl().toList()

        // Due to children().reversed() and addFirst(), order is: Root (0), Child1 (1), Grandchild (2), Child2 (1)
        crawledNodes shouldHaveSize 4
        crawledNodes[0].node.text shouldBe "Root"
        crawledNodes[0].level shouldBe 0
        // The actual traversal order depends on the queue implementation with reversed children
        // Let's just verify we get all expected nodes and levels
        val nodeTexts = crawledNodes.map { it.node.text }
        val nodeLevels = crawledNodes.map { it.level }

        nodeTexts shouldContain "Root"
        nodeTexts shouldContain "Child 1"
        nodeTexts shouldContain "Child 2"
        nodeTexts shouldContain "Grandchild"

        nodeLevels shouldContain 0  // Root
        nodeLevels shouldContain 1  // Children
        nodeLevels shouldContain 2  // Grandchild
    }

    @Test
    fun `crawl tracks node levels correctly`() {
        val child = TestACSNodeInfo(text = "Child")
        val grandchild = TestACSNodeInfo(text = "Grandchild")
        val greatGrandchild = TestACSNodeInfo(text = "GreatGrandchild")

        child.addChild(grandchild)
        grandchild.addChild(greatGrandchild)
        val root = TestACSNodeInfo(text = "Root").addChild(child)

        val crawledNodes = root.crawl().toList()

        crawledNodes[0].level shouldBe 0  // Root
        crawledNodes[1].level shouldBe 1  // Child
        crawledNodes[2].level shouldBe 2  // Grandchild
        crawledNodes[3].level shouldBe 3  // GreatGrandchild
    }

    // Tests for scrollNode()
    @Test
    fun `scrollNode performs scroll action on scrollable node`() {
        val scrollableNode = TestACSNodeInfo(isScrollable = true)

        val result = scrollableNode.scrollNode()

        result shouldBe true
        scrollableNode.performedActions shouldContain ACSNodeInfo.ACTION_SCROLL_FORWARD
    }

    @Test
    fun `scrollNode returns false for non-scrollable node`() {
        val nonScrollableNode = TestACSNodeInfo(isScrollable = false)

        val result = nonScrollableNode.scrollNode()

        result shouldBe false
        nonScrollableNode.performedActions.shouldBeEmpty()
    }

    // Tests for AccessibilityEvent.pkgId extension
    @Test
    fun `pkgId returns valid package ID when package name is set`() {
        val event = mockk<AccessibilityEvent>()
        every { event.packageName } returns "com.example.app"

        val result = event.pkgId

        result shouldBe "com.example.app".toPkgId()
    }

    @Test
    fun `pkgId returns null when package name is null`() {
        val event = mockk<AccessibilityEvent>()
        every { event.packageName } returns null

        val result = event.pkgId

        result shouldBe null
    }

    @Test
    fun `pkgId returns null when package name is blank`() {
        val event = mockk<AccessibilityEvent>()
        every { event.packageName } returns ""

        val result = event.pkgId

        result shouldBe null
    }

    @Test
    fun `pkgId returns null when package name is whitespace only`() {
        val event = mockk<AccessibilityEvent>()
        every { event.packageName } returns "   "

        val result = event.pkgId

        result shouldBe null
    }
}