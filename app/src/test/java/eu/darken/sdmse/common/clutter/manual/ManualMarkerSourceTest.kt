package eu.darken.sdmse.common.clutter.manual

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ManualMarkerSourceTest : BaseTest() {

    private var markerTestTool = MarkerSourceTestTool("./src/main/assets/clutter/db_clutter_markers.json")

    @BeforeEach
    fun setup() = testEnv {
        markerTestTool.checkBasics()
    }

    @AfterEach
    fun teardown() {
    }

    private fun testEnv(block: suspend MarkerSourceTestTool.() -> Unit) {
        runTest { block(markerTestTool) }
    }

    @Test fun `test coolreader manuals`() = testEnv {
        neg(SDCARD, "Books/somebook.pdf")
        pos(SDCARD, "org.coolreader", "Books/cr3_manual_en_US.fb2")
        pos(SDCARD, "org.coolreader", "Books/cr3_manual_ru_RU.fb2")
    }

}