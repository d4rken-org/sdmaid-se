package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
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
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    @Test fun testDefaults() = runTest {
        addDefaultNegatives()
        addCandidate(neg().prefixFree("com.some.app/offlinecache/"))
        addCandidate(pos().prefixFree("com.some.app/offlinecache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/offline-cache/"))
        addCandidate(pos().prefixFree("com.some.app/offline-cache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/offline_cache/"))
        addCandidate(pos().prefixFree("com.some.app/offline_cache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.offlinecache/"))
        addCandidate(pos().prefixFree("com.some.app/.offlinecache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.offline-cache/"))
        addCandidate(pos().prefixFree("com.some.app/.offline-cache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.offline-cache/"))
        addCandidate(pos().prefixFree("com.some.app/.offline_cache/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.offline_cache/.nomedia"))
        addCandidate(
            neg().pkgs(testPkg).prefixFree("$testPkg/files/UnityCache")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs(testPkg).prefixFree("$testPkg/files/UnityCache/$rngString")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }

    @Test fun testWebViewFilter() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.spotify.music").prefixFree("com.spotify.music/files/spotifycache")
                .locs(PUBLIC_DATA)
        )
        addCandidate(
            pos().pkgs("com.spotify.music").prefixFree("com.spotify.music/files/spotifycache/$rngString")
                .locs(PUBLIC_DATA)
        )
        confirm(create())
    }

    @Test fun testSpotifyCache() = runTest {
        addDefaultNegatives()
        addCandidate(neg().prefixFree("com.spotify.music/files/spotifycache").locs(PUBLIC_DATA))
        addCandidate(pos().prefixFree("com.spotify.music/files/spotifycache/$rngString").locs(PUBLIC_DATA))
        confirm(create())
    }

    @Test fun testNaviKing() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.kingwaytek.naviking").prefixFree("LocalKingMapN5").locs(SDCARD))
        addCandidate(
            pos().pkgs("com.kingwaytek.naviking").prefixFree("LocalKingMapN5/$rngString").locs(SDCARD)
        )
        confirm(create())
    }

    @Test fun testXDALabs() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.xda.labs").prefixFree("com.xda.labs/files/something").locs(PUBLIC_DATA))
        addCandidate(
            pos().pkgs("com.xda.labs")
                .prefixFree("com.xda.labs/files/8bc11e76-2d70-4270-8615-c0a40a29ba09_2020070801.apk")
                .locs(PUBLIC_DATA)
        )
        addCandidate(
            pos().pkgs("com.xda.labs").prefixFree("com.xda.labs/files/78d77e4c-08d2-47b4-a804-7785d8eb9a8a_1.apk")
                .locs(PUBLIC_DATA)
        )
        confirm(create())
    }

    @Test fun testInshot() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.camerasideas.instashot").prefixFree("inshot/.sound").locs(SDCARD))
        addCandidate(
            pos().pkgs("com.camerasideas.instashot").prefixFree("inshot/.sound/$rngString").locs(SDCARD)
        )
        confirm(create())
    }

    @Test fun testVlc() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("org.videolan.vlc").prefixFree("org.videolan.vlc/files/subtitles").locs(PUBLIC_DATA)
        )
        addCandidate(
            pos().pkgs("org.videolan.vlc").prefixFree("org.videolan.vlc/files/subtitles/$rngString")
                .locs(PUBLIC_DATA)
        )
        confirm(create())
    }

    @Test fun gplayInstantApps() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.android.vending").prefixFree("com.android.vending/files/dna_data/something")
                .locs(PUBLIC_DATA)
        )
        addCandidate(
            pos().pkgs("com.android.vending").prefixFree("com.android.vending/files/dna_data/FullArchive-403076282")
                .locs(PUBLIC_DATA)
        )
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
}