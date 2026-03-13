package testhelpers

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AcsDebugParserTest : BaseTest() {

    @Test
    fun `parseLine extracts level from dashes`() {
        val level0 = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=8b03c20, bounds=Rect(0, 0 - 1220, 2712)"
        )
        val level1 = AcsDebugParser.parseLine(
            "ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=3662a7f, bounds=Rect(0, 0 - 1220, 2712)"
        )
        val level2 = AcsDebugParser.parseLine(
            "ACS-DEBUG: --2: text='App info', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=com.android.settings:id/title pkg=com.android.settings, identity=5a45202, bounds=Rect(84, 298 - 471, 437)"
        )

        level0.shouldNotBeNull()
        level0.level shouldBe 0

        level1.shouldNotBeNull()
        level1.level shouldBe 1

        level2.shouldNotBeNull()
        level2.level shouldBe 2
    }

    @Test
    fun `parseLine extracts text property`() {
        val withText = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='Storage', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )
        val nullText = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )

        withText.shouldNotBeNull()
        withText.text shouldBe "Storage"

        nullText.shouldNotBeNull()
        nullText.text.shouldBeNull()
    }

    @Test
    fun `parseLine extracts clickable and enabled properties`() {
        val clickableEnabled = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='Button', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=null pkg=test, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )
        val notClickableDisabled = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='Label', class=android.widget.TextView, clickable=false, checkable=false enabled=false, id=null pkg=test, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )

        clickableEnabled.shouldNotBeNull()
        clickableEnabled.isClickable shouldBe true
        clickableEnabled.isEnabled shouldBe true

        notClickableDisabled.shouldNotBeNull()
        notClickableDisabled.isClickable shouldBe false
        notClickableDisabled.isEnabled shouldBe false
    }

    @Test
    fun `parseLine extracts viewIdResourceName`() {
        val withId = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='Storage', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )
        val nullId = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )

        withId.shouldNotBeNull()
        withId.viewIdResourceName shouldBe "android:id/title"

        nullId.shouldNotBeNull()
        nullId.viewIdResourceName.shouldBeNull()
    }

    @Test
    fun `parseLine extracts className and packageName`() {
        val node = AcsDebugParser.parseLine(
            "ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)"
        )

        node.shouldNotBeNull()
        node.className shouldBe "android.widget.FrameLayout"
        node.packageName shouldBe "com.android.settings"
    }

    @Test
    fun `parseTree builds simple parent-child tree`() {
        val log = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=abc, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: -1: text='Storage', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=def, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: -1: text='Clear cache', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=null pkg=com.android.settings, identity=ghi, bounds=Rect(0, 0 - 100, 50)
        """.trimIndent()

        val root = AcsDebugParser.parseTree(log)

        root.shouldNotBeNull()
        root.text.shouldBeNull()
        root.className shouldBe "android.widget.FrameLayout"
        root.childCount shouldBe 2

        val storage = root.getChild(0) as TestACSNodeInfo
        storage.text shouldBe "Storage"
        storage.isClickable shouldBe false

        val clearCache = root.getChild(1) as TestACSNodeInfo
        clearCache.text shouldBe "Clear cache"
        clearCache.isClickable shouldBe true
    }

    @Test
    fun `parseTree builds nested tree`() {
        val log = """
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=test, identity=a, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: -1: text='null', class=android.widget.LinearLayout, clickable=false, checkable=false enabled=true, id=null pkg=test, identity=b, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: --2: text='Title', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=null pkg=test, identity=c, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: --2: text='Subtitle', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=null pkg=test, identity=d, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG: -1: text='Button', class=android.widget.Button, clickable=true, checkable=false enabled=true, id=null pkg=test, identity=e, bounds=Rect(0, 0 - 100, 50)
        """.trimIndent()

        val root = AcsDebugParser.parseTree(log)

        root.shouldNotBeNull()
        root.childCount shouldBe 2

        val linearLayout = root.getChild(0) as TestACSNodeInfo
        linearLayout.className shouldBe "android.widget.LinearLayout"
        linearLayout.childCount shouldBe 2

        val title = linearLayout.getChild(0) as TestACSNodeInfo
        title.text shouldBe "Title"

        val subtitle = linearLayout.getChild(1) as TestACSNodeInfo
        subtitle.text shouldBe "Subtitle"

        val button = root.getChild(1) as TestACSNodeInfo
        button.text shouldBe "Button"
        button.isClickable shouldBe true
    }

    @Test
    fun `parseTree ignores START and STOP lines`() {
        val log = """
            ACS-DEBUG -- abc123 -- START -- EventType: TYPE_WINDOW_CONTENT_CHANGED
            ACS-DEBUG: 0: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=test, identity=a, bounds=Rect(0, 0 - 100, 50)
            ACS-DEBUG -- abc123 -- STOP -- ---
        """.trimIndent()

        val root = AcsDebugParser.parseTree(log)

        root.shouldNotBeNull()
        root.childCount shouldBe 0
    }

    @Test
    fun `parseTree returns null for empty input`() {
        AcsDebugParser.parseTree("").shouldBeNull()
        AcsDebugParser.parseTree("no acs debug lines here").shouldBeNull()
    }

    @Test
    fun `parseTree with real HyperOS log snippet`() {
        // Real snippet from HyperOS showing Storage section
        val log = """
            ACS-DEBUG: ------------12: text='null', class=android.widget.FrameLayout, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=493c896, bounds=Rect(0, 1972 - 1220, 2086)
            ACS-DEBUG: -------------13: text='Storage', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=e0f78b8, bounds=Rect(91, 1972 - 1129, 2086)
            ACS-DEBUG: ------------12: text='null', class=android.view.ViewGroup, clickable=true, checkable=false enabled=true, id=null pkg=com.android.settings, identity=c4d7bb1, bounds=Rect(39, 2086 - 1181, 2269)
            ACS-DEBUG: -------------13: text='null', class=android.view.View, clickable=false, checkable=false enabled=true, id=null pkg=com.android.settings, identity=673a1f7, bounds=Rect(91, 2094 - 91, 2094)
            ACS-DEBUG: -------------13: text='Total', class=android.widget.TextView, clickable=false, checkable=false enabled=true, id=android:id/title pkg=com.android.settings, identity=b4349f6, bounds=Rect(91, 2140 - 215, 2214)
        """.trimIndent()

        val root = AcsDebugParser.parseTree(log)

        root.shouldNotBeNull()
        // Level 12 is the root in this snippet, should have "Storage" label header child
        root.className shouldBe "android.widget.FrameLayout"
        root.childCount shouldBe 1

        val storageLabel = root.getChild(0) as TestACSNodeInfo
        storageLabel.text shouldBe "Storage"
    }
}
