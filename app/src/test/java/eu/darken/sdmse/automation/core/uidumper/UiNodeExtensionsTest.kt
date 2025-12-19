package eu.darken.sdmse.automation.core.uidumper

import android.graphics.Rect
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UiNodeExtensionsTest : BaseTest() {

    private fun createNode(
        text: String? = null,
        contentDesc: String? = null,
        resourceId: String? = null,
        children: List<UiNode> = emptyList(),
    ) = UiNode(
        text = text,
        contentDesc = contentDesc,
        resourceId = resourceId,
        className = "android.widget.Button",
        bounds = Rect(0, 0, 100, 100),
        isClickable = true,
        isEnabled = true,
        children = children,
    )

    @Test
    fun `findByText finds node with matching text`() {
        val target = createNode(text = "Clear cache")
        val root = createNode(children = listOf(target))

        val result = root.findByText("Clear cache")

        result shouldNotBe null
        result!!.text shouldBe "Clear cache"
    }

    @Test
    fun `findByText is case insensitive`() {
        val target = createNode(text = "Clear Cache")
        val root = createNode(children = listOf(target))

        val result = root.findByText("clear cache")

        result shouldNotBe null
        result!!.text shouldBe "Clear Cache"
    }

    @Test
    fun `findByText finds partial match`() {
        val target = createNode(text = "Clear cache for this app")
        val root = createNode(children = listOf(target))

        val result = root.findByText("Clear cache")

        result shouldNotBe null
    }

    @Test
    fun `findByText returns null when not found`() {
        val root = createNode(text = "Some other text")

        val result = root.findByText("Clear cache")

        result shouldBe null
    }

    @Test
    fun `findByExactText finds exact match`() {
        val target = createNode(text = "Clear cache")
        val root = createNode(children = listOf(target))

        val result = root.findByExactText("Clear cache")

        result shouldNotBe null
    }

    @Test
    fun `findByExactText does not find partial match`() {
        val target = createNode(text = "Clear cache for this app")
        val root = createNode(children = listOf(target))

        val result = root.findByExactText("Clear cache")

        result shouldBe null
    }

    @Test
    fun `findByContentDesc finds node with matching content-desc`() {
        val target = createNode(contentDesc = "Clear cache")
        val root = createNode(children = listOf(target))

        val result = root.findByContentDesc("Clear cache")

        result shouldNotBe null
        result!!.contentDesc shouldBe "Clear cache"
    }

    @Test
    fun `findByContentDesc is case insensitive`() {
        val target = createNode(contentDesc = "Clear Cache")
        val root = createNode(children = listOf(target))

        val result = root.findByContentDesc("clear cache")

        result shouldNotBe null
    }

    @Test
    fun `findByResourceId finds node with matching resource id`() {
        val target = createNode(resourceId = "com.android.settings:id/button2")
        val root = createNode(children = listOf(target))

        val result = root.findByResourceId("button2")

        result shouldNotBe null
        result!!.resourceId shouldBe "com.android.settings:id/button2"
    }

    @Test
    fun `findByTexts finds first matching text from collection`() {
        val target = createNode(text = "Borrar caché")
        val root = createNode(children = listOf(target))

        val result = root.findByTexts(listOf("Clear cache", "Borrar caché", "Löschen"))

        result shouldNotBe null
        result!!.text shouldBe "Borrar caché"
    }

    @Test
    fun `findByTexts returns null when none match`() {
        val root = createNode(text = "Some text")

        val result = root.findByTexts(listOf("Clear cache", "Borrar caché"))

        result shouldBe null
    }

    @Test
    fun `findByContentDescs finds first matching content-desc from collection`() {
        val target = createNode(contentDesc = "Clear cache")
        val root = createNode(children = listOf(target))

        val result = root.findByContentDescs(listOf("Clear storage", "Clear cache"))

        result shouldNotBe null
        result!!.contentDesc shouldBe "Clear cache"
    }

    @Test
    fun `findByText searches nested children`() {
        val deepChild = createNode(text = "Clear cache")
        val middleChild = createNode(children = listOf(deepChild))
        val root = createNode(children = listOf(middleChild))

        val result = root.findByText("Clear cache")

        result shouldNotBe null
        result!!.text shouldBe "Clear cache"
    }
}
