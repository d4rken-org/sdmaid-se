package eu.darken.sdmse.automation.core.uidumper

import android.graphics.Rect
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class UiDumperTest : BaseTest() {

    private val adbManager: AdbManager = mockk()
    private val rootManager: RootManager = mockk()
    private val shellOps: ShellOps = mockk()

    private fun createDumper() = UiDumper(
        adbManager = adbManager,
        rootManager = rootManager,
        shellOps = shellOps,
    )

    @Test
    fun `parseBounds parses valid bounds string`() {
        val dumper = createDumper()

        val bounds = dumper.parseBounds("[0,0][1080,2400]")

        bounds shouldBe Rect(0, 0, 1080, 2400)
    }

    @Test
    fun `parseBounds parses complex bounds`() {
        val dumper = createDumper()

        val bounds = dumper.parseBounds("[540,959][1020,1239]")

        bounds shouldBe Rect(540, 959, 1020, 1239)
    }

    @Test
    fun `parseBounds returns empty rect for null`() {
        val dumper = createDumper()

        val bounds = dumper.parseBounds(null)

        bounds shouldBe Rect()
    }

    @Test
    fun `parseBounds returns empty rect for invalid format`() {
        val dumper = createDumper()

        val bounds = dumper.parseBounds("invalid")

        bounds shouldBe Rect()
    }

    @Test
    fun `parseXml parses simple hierarchy`() {
        val dumper = createDumper()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
                    content-desc="" checkable="false" checked="false" clickable="false"
                    enabled="true" focusable="false" bounds="[0,0][1080,2400]" />
            </hierarchy>
        """.trimIndent()

        val root = dumper.parseXml(xml)

        root shouldNotBe null
        root!!.className shouldBe "android.widget.FrameLayout"
        root.bounds shouldBe Rect(0, 0, 1080, 2400)
        root.isEnabled shouldBe true
        root.isClickable shouldBe false
    }

    @Test
    fun `parseXml parses nested nodes`() {
        val dumper = createDumper()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
                    content-desc="" checkable="false" checked="false" clickable="false"
                    enabled="true" focusable="false" bounds="[0,0][1080,2400]">
                <node index="0" text="Clear cache" resource-id="com.android.settings:id/button2"
                      class="android.widget.Button" content-desc="" checkable="false" checked="false"
                      clickable="true" enabled="true" focusable="true" bounds="[540,959][1020,1239]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val root = dumper.parseXml(xml)

        root shouldNotBe null
        root!!.children.size shouldBe 1
        val button = root.children[0]
        button.text shouldBe "Clear cache"
        button.resourceId shouldBe "com.android.settings:id/button2"
        button.className shouldBe "android.widget.Button"
        button.isClickable shouldBe true
        button.bounds shouldBe Rect(540, 959, 1020, 1239)
    }

    @Test
    fun `parseXml extracts content-desc`() {
        val dumper = createDumper()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="com.android.settings:id/action2"
                    class="android.widget.LinearLayout" content-desc="Clear cache"
                    checkable="false" checked="false" clickable="true"
                    enabled="true" focusable="false" bounds="[540,861][1020,1107]" />
            </hierarchy>
        """.trimIndent()

        val root = dumper.parseXml(xml)

        root shouldNotBe null
        root!!.contentDesc shouldBe "Clear cache"
        root.text shouldBe null
        root.resourceId shouldBe "com.android.settings:id/action2"
    }

    @Test
    fun `parseXml handles empty text as null`() {
        val dumper = createDumper()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
                    content-desc="" checkable="false" checked="false" clickable="false"
                    enabled="true" focusable="false" bounds="[0,0][1080,2400]" />
            </hierarchy>
        """.trimIndent()

        val root = dumper.parseXml(xml)

        root shouldNotBe null
        root!!.text shouldBe null
        root.contentDesc shouldBe null
        root.resourceId shouldBe null
    }

    @Test
    fun `parseXml handles multiple siblings`() {
        val dumper = createDumper()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout"
                    content-desc="" checkable="false" checked="false" clickable="false"
                    enabled="true" focusable="false" bounds="[0,0][1080,2400]">
                <node index="0" text="Clear storage" resource-id="com.android.settings:id/button1"
                      class="android.widget.Button" content-desc="" checkable="false" checked="false"
                      clickable="true" enabled="true" focusable="true" bounds="[60,959][540,1239]" />
                <node index="1" text="Clear cache" resource-id="com.android.settings:id/button2"
                      class="android.widget.Button" content-desc="" checkable="false" checked="false"
                      clickable="true" enabled="true" focusable="true" bounds="[540,959][1020,1239]" />
              </node>
            </hierarchy>
        """.trimIndent()

        val root = dumper.parseXml(xml)

        root shouldNotBe null
        root!!.children.size shouldBe 2
        root.children[0].text shouldBe "Clear storage"
        root.children[1].text shouldBe "Clear cache"
    }

    @Test
    fun `UiNode centerX and centerY return correct values`() {
        val node = UiNode(
            text = "Test",
            contentDesc = null,
            resourceId = null,
            className = "android.widget.Button",
            bounds = Rect(100, 200, 300, 400),
            isClickable = true,
            isEnabled = true,
            children = emptyList(),
        )

        node.centerX shouldBe 200
        node.centerY shouldBe 300
    }

    @Test
    fun `UiNode flatten returns all nodes`() {
        val child1 = UiNode(
            text = "Child1", contentDesc = null, resourceId = null,
            className = null, bounds = Rect(), isClickable = false,
            isEnabled = true, children = emptyList(),
        )
        val child2 = UiNode(
            text = "Child2", contentDesc = null, resourceId = null,
            className = null, bounds = Rect(), isClickable = false,
            isEnabled = true, children = emptyList(),
        )
        val parent = UiNode(
            text = "Parent", contentDesc = null, resourceId = null,
            className = null, bounds = Rect(), isClickable = false,
            isEnabled = true, children = listOf(child1, child2),
        )

        val flattened = parent.flatten()

        flattened.size shouldBe 3
        flattened[0].text shouldBe "Parent"
        flattened[1].text shouldBe "Child1"
        flattened[2].text shouldBe "Child2"
    }
}
