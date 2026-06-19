package eu.darken.sdmse.automation.core.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ACSNodeInfoExtensionsTest : BaseTest() {

    private fun bounds(left: Int, top: Int, right: Int, bottom: Int) =
        ACSNodeInfo.ScreenBounds(left = left, top = top, right = right, bottom = bottom)

    @Test
    fun `screen bounds isEmpty - normal on-screen rect is not empty`() {
        // git.artdeell.mojo from issue #2464: valid, mid-screen → tappable
        bounds(64, 801, 560, 839).isEmpty() shouldBe false
    }

    @Test
    fun `screen bounds isEmpty - degenerate rect clipped behind nav bar`() {
        // The 3 failing rows from issue #2464: top >= bottom (clipped at the nav-bar boundary)
        bounds(64, 1519, 560, 1504).isEmpty() shouldBe true
        bounds(64, 1650, 560, 1504).isEmpty() shouldBe true
        bounds(64, 1541, 560, 1504).isEmpty() shouldBe true
    }

    @Test
    fun `screen bounds isEmpty - zero height or width is empty`() {
        bounds(64, 100, 560, 100).isEmpty() shouldBe true
        bounds(300, 100, 300, 500).isEmpty() shouldBe true
        bounds(0, 0, 0, 0).isEmpty() shouldBe true
    }
}
