package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
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
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2084
     * [HiddenFilterTest.keepUnityOfflineGameData]
     */
    @Test fun testUnity3dGameData() = runTest {
        addCandidate(neg().pkgs("test.pkg").locs(PUBLIC_DATA).prefixFree("test.pkg/files/Cache"))
        addCandidate(neg().pkgs("test.pkg").locs(PUBLIC_DATA).prefixFree("test.pkg/files/Cache/$rngString"))
        addCandidate(
            pos().pkgs("test.pkg").locs(PUBLIC_DATA).prefixFree("test.pkg/files/Cache/t_head_mask_back.tga.unity3d")
        )
        addCandidate(
            pos().pkgs("test.pkg").locs(PUBLIC_DATA)
                .prefixFree("test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986")
        )
        addCandidate(
            pos().pkgs("test.pkg").locs(PUBLIC_DATA)
                .prefixFree("test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986&lol=123asd")
        )
        addCandidate(
            pos().pkgs("test.pkg").locs(PUBLIC_DATA)
                .prefixFree("test.pkg/files/Cache/$rngString.unity3d&ux=$rngString")
        )
        confirm(create())
    }

    @Test fun testUnigineOilRush() = runTest {
        addCandidate(
            neg().pkgs("com.unigine.oilrush", "com.unigine.oilrush_full").locs(SDCARD)
                .prefixFree("unigine_oilrush/profiles")
        )
        addCandidate(
            pos().pkgs("com.unigine.oilrush", "com.unigine.oilrush_full").locs(SDCARD)
                .prefixFree("unigine_oilrush/game.cache")
        )
        addCandidate(
            pos().pkgs("com.unigine.oilrush", "com.unigine.oilrush_full").locs(SDCARD)
                .prefixFree("unigine_oilrush/menu.cache")
        )
        addCandidate(
            pos().pkgs("com.unigine.oilrush", "com.unigine.oilrush_full").locs(SDCARD)
                .prefixFree("unigine_oilrush/log.html")
        )
        confirm(create())
    }

    @Test fun testUnityCache() = runTest {
        addCandidate(
            neg().pkgs(testPkg).prefixFree("$testPkg/files/UnityCache").locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs(testPkg).prefixFree("$testPkg/files/UnityCache/$rngString")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }
}