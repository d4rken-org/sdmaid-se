package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GameFilesFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = GameFilesFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2084
     * [HiddenFilterTest.keepUnityOfflineGameData]
     */
    @Test fun `test unity3d game data`() = runTest {
        neg("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache")
        neg("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/$rngString")
        pos("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/t_head_mask_back.tga.unity3d")
        pos("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986")
        pos("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986&lol=123asd")
        pos("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/$rngString.unity3d&ux=$rngString")
        confirm(create())
    }

    @Test fun `test unigine oil rush`() = runTest {
        neg(setOf("com.unigine.oilrush", "com.unigine.oilrush_full"), setOf(SDCARD), "unigine_oilrush/profiles")
        pos(setOf("com.unigine.oilrush", "com.unigine.oilrush_full"), setOf(SDCARD), "unigine_oilrush/game.cache")
        pos(setOf("com.unigine.oilrush", "com.unigine.oilrush_full"), setOf(SDCARD), "unigine_oilrush/menu.cache")
        pos(setOf("com.unigine.oilrush", "com.unigine.oilrush_full"), setOf(SDCARD), "unigine_oilrush/log.html")
        confirm(create())
    }

    @Test fun `test unity cache`() = runTest {
        neg(testPkg, setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/UnityCache")
        pos(testPkg, setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/UnityCache/$rngString")
        confirm(create())
    }
}