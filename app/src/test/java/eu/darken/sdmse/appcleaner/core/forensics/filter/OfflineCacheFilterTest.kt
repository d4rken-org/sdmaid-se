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

class OfflineCacheFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = OfflineCacheFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test defaults`() = runTest {
        addDefaultNegatives()
        neg(testPkg, PUBLIC_DATA, "com.some.app/offlinecache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/offlinecache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/offline-cache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/offline-cache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/offline_cache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/offline_cache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/.offlinecache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/.offlinecache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/.offline-cache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/.offline-cache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/.offline-cache")
        pos(testPkg, PUBLIC_DATA, "com.some.app/.offline_cache/$rngString")
        neg(testPkg, PUBLIC_DATA, "com.some.app/.offline_cache/.nomedia")
        neg(testPkg, SDCARD, "$testPkg/files/UnityCache")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/UnityCache")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/UnityCache")
        neg(testPkg, SDCARD, "$testPkg/files/UnityCache/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/UnityCache/$rngString")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/UnityCache/$rngString")
        confirm(create())
    }

    @Test fun `test webview filter`() = runTest {
        addDefaultNegatives()
        neg("com.spotify.music", PUBLIC_DATA, "com.spotify.music/files/spotifycache")
        pos("com.spotify.music", PUBLIC_DATA, "com.spotify.music/files/spotifycache/$rngString")
        confirm(create())
    }

    @Test fun `test spotify cache`() = runTest {
        addDefaultNegatives()
        neg("com.spotify.music", PUBLIC_DATA, "com.spotify.music/files/spotifycache")
        pos("com.spotify.music", PUBLIC_DATA, "com.spotify.music/files/spotifycache/$rngString")
        confirm(create())
    }

    @Test fun `test navi king`() = runTest {
        addDefaultNegatives()
        neg("com.kingwaytek.naviking", SDCARD, "LocalKingMapN5")
        pos("com.kingwaytek.naviking", SDCARD, "LocalKingMapN5/$rngString")
        confirm(create())
    }

    @Test fun `test xda labs`() = runTest {
        addDefaultNegatives()
        neg("com.xda.labs", PUBLIC_DATA, "com.xda.labs/files/something")
        pos("com.xda.labs", PUBLIC_DATA, "com.xda.labs/files/8bc11e76-2d70-4270-8615-c0a40a29ba09_2020070801.apk")
        pos("com.xda.labs", PUBLIC_DATA, "com.xda.labs/files/78d77e4c-08d2-47b4-a804-7785d8eb9a8a_1.apk")
        confirm(create())
    }

    @Test fun `test inshot`() = runTest {
        addDefaultNegatives()
        neg("com.camerasideas.instashot", SDCARD, "inshot/.sound")
        pos("com.camerasideas.instashot", SDCARD, "inshot/.sound/$rngString")
        confirm(create())
    }

    @Test fun `test vlc`() = runTest {
        addDefaultNegatives()
        neg("org.videolan.vlc", PUBLIC_DATA, "org.videolan.vlc/files/subtitles")
        pos("org.videolan.vlc", PUBLIC_DATA, "org.videolan.vlc/files/subtitles/$rngString")
        confirm(create())
    }

    @Test fun `test gplay instant apps`() = runTest {
        addDefaultNegatives()
        neg("com.android.vending", PUBLIC_DATA, "com.android.vending/files/dna_data/something")
        pos("com.android.vending", PUBLIC_DATA, "com.android.vending/files/dna_data/FullArchive-403076282")
        confirm(create())
    }

    @Test fun `coolreader manuals`() = runTest {
        addDefaultNegatives()
        neg("org.coolreader", SDCARD, "Books/book.fb2")
        neg("org.coolreader", SDCARD, "Books/book.pdf")
        pos("org.coolreader", SDCARD, "Books/cr3_manual_ru_RU.fb2")
        pos("org.coolreader", SDCARD, "Books/cr3_manual_de_DE.fb2")
        confirm(create())
    }

    @Test fun `estrongs icon cache`() = runTest {
        addDefaultNegatives()
        val pkgs = setOf(
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.cupcake",
            "com.estrongs.android.pop.app.shortcut",
            "com.estrongs.android.pop.pro"
        )
        pkgs.forEach {
            neg(it, SDCARD, ".estrongs/.app_icon_back")
            pos(it, SDCARD, ".estrongs/.app_icon_back/ver")
            pos(it, SDCARD, ".estrongs/.app_icon_back/com.zebu.hitrosti.png")
        }
        confirm(create())
    }

    @Test fun `SHAREit offline`() = runTest {
        val pkgs = setOf(
            "com.lenovo.anyshare.gps",
            "com.lenovo.anyshare",
            "shareit.lite",
            "shareit.premium",
        )
        pkgs.forEach {
            neg(it, PUBLIC_DATA, "$it/files/SHAREit/.offline")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit/.offline/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.offline")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.offline/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.offline")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.offline/anything")
            neg(it, SDCARD, "SHAREit/.offline")
            pos(it, SDCARD, "SHAREit/.offline/anything")
            neg(it, SDCARD, "SHAREit Lite/.offline")
            pos(it, SDCARD, "SHAREit Lite/.offline/anything")
            neg(it, SDCARD, "SHAREit Premium/.offline")
            pos(it, SDCARD, "SHAREit Premium/.offline/anything")
        }
        confirm(create())
    }
}