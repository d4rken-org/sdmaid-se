package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThumbnailsFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ThumbnailsFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        environment = storageEnvironment,
    )

    @Test fun testHiddenCacheDefaults() = runTest {
        addDefaultNegatives()

        addCandidate(
            neg().pkgs(testPkg).prefixFree("$testPkg/.thumbnails")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs(testPkg).prefixFree("$testPkg/.thumbnails/file")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            neg().pkgs(testPkg).prefixFree("$testPkg/files/.thumbnails")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        addCandidate(
            pos().pkgs(testPkg).prefixFree("$testPkg/files/.thumbnails/file")
                .locs(SDCARD, PUBLIC_DATA, PRIVATE_DATA)
        )
        confirm(create())
    }

    @Test fun testFilterQuickOffice() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.quickoffice.android").locs(PRIVATE_DATA).prefixFree("com.quickoffice.android/files")
        )
        addCandidate(
            neg().pkgs("com.quickoffice.android").locs(PRIVATE_DATA).prefixFree("com.quickoffice.android/files/asdasd")
        )
        addCandidate(
            pos().pkgs("com.quickoffice.android").locs(PRIVATE_DATA)
                .prefixFree("com.quickoffice.android/files/kljalskdjlkjasd.thumb.panel")
        )
        addCandidate(
            pos().pkgs("com.quickoffice.android").locs(PRIVATE_DATA)
                .prefixFree("com.quickoffice.android/files/kljalskdjlkjasd.thumb.home")
        )
        confirm(create())
    }

    @Test fun testFilterNextAppFx() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("nextapp.fx").locs(SDCARD).prefixFree(".FX/CloudThumbnail"))
        addCandidate(
            pos().pkgs("nextapp.fx").locs(SDCARD).prefixFree(".FX/CloudThumbnail/$rngString")
        )
        addCandidate(neg().pkgs("nextapp.fx").locs(SDCARD).prefixFree(".FX/ImageThumbnail"))
        addCandidate(
            pos().pkgs("nextapp.fx").locs(SDCARD).prefixFree(".FX/ImageThumbnail/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterInfzmReader() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("net.coollet.infzmreader").locs(SDCARD).prefixFree("infzm/thumb_image"))
        addCandidate(
            pos().pkgs("net.coollet.infzmreader").locs(SDCARD)
                .prefixFree("infzm/thumb_image/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterKascendVideo() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.kascend.video").locs(SDCARD).prefixFree("kascend/videoshow/.thumbcache"))
        addCandidate(
            pos().pkgs("com.kascend.video").locs(SDCARD)
                .prefixFree("kascend/videoshow/.thumbcache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterWalloid() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.hashcode.walloidpro", "com.hashcode.walloid").locs(SDCARD).prefixFree("Walloid/.Thumbnail")
        )
        addCandidate(
            pos().pkgs("com.hashcode.walloidpro", "com.hashcode.walloid").locs(SDCARD)
                .prefixFree("Walloid/.Thumbnail/$rngString")
        )
        confirm(create())
    }

    @Test fun testVideoPlayer() = runTest {
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD).prefixFree(".thumbnails/MAKO-6.0.1/movie_10326")
        )
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD)
                .prefixFree(".thumbnails/MAKO-6.0.1/movie_10326/camera.ListView.lvl")
        )
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD)
                .prefixFree(".thumbnails/MAKO-6.0.1/movie_156560/instaweather_vid_20141227_085331.ListView.lvl")
        )
        addCandidate(neg().pkgs("com.sec.android.app.videoplayer").locs(SDCARD).prefixFree(".thumbnails/MAKO-6.0.1"))
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD)
                .prefixFree(".thumbnails/I9505XXUEMKF_4.3/movie_10326")
        )
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD)
                .prefixFree(".thumbnails/I9505XXUEMKF_4.3/movie_10326/camera.ListView.lvl")
        )
        addCandidate(
            pos().pkgs("com.sec.android.app.videoplayer").locs(SDCARD)
                .prefixFree(".thumbnails/I9505XXUEMKF_4.3/movie_156560/instaweather_vid_20141227_085331.ListView.lvl")
        )
        addCandidate(
            neg().pkgs("com.sec.android.app.videoplayer").locs(SDCARD).prefixFree(".thumbnails/I9505XXUEMKF_4.3")
        )
        addCandidate(neg().pkgs("com.sec.android.app.videoplayer").locs(SDCARD).prefixFree(".thumbnails"))
        confirm(create())
    }

    @Test fun testSamsungMyFilesThumbnails() = runTest {
        addCandidate(neg().pkgs("com.sec.android.app.myfiles").locs(SDCARD).prefixFree("Movies/.thumbnails"))
        addCandidate(pos().pkgs("com.sec.android.app.myfiles").locs(SDCARD).prefixFree("Movies/.thumbnails/something"))
        confirm(create())
    }

    @Test fun testMiuiPlayer() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.miui.player").locs(SDCARD).prefixFree("MIUI/music/album/.player_thumb"))
        addCandidate(
            pos().pkgs("com.miui.player").locs(SDCARD).prefixFree("MIUI/music/album/.player_thumb/file")
        )
        confirm(create())
    }

    @Test fun testMIUIVideoThumbs() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.miui.videoplayer").locs(SDCARD).prefixFree("MIUI/Video/thumb/"))
        addCandidate(
            pos().pkgs("com.miui.videoplayer").locs(SDCARD)
                .prefixFree("MIUI/Video/thumb/6ba49cfe32916e890491ee101f97424d.thumb")
        )
        confirm(create())
    }

    @Test fun testMovieFx() = runTest {
        addCandidate(neg().pkgs("tv.waterston.movieridefx").locs(SDCARD).prefixFree("MovieRideFX/thumbs"))
        addCandidate(pos().pkgs("tv.waterston.movieridefx").locs(SDCARD).prefixFree("MovieRideFX/thumbs/test"))
        confirm(create())

    }

    @Test fun `viber user photo thumbs`() = runTest {
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/User photos/.thumbnails")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/User photos/.thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `FxGuru thumbs`() = runTest {
        neg("com.picadelic.fxguru", SDCARD, "FxGuru/thumbnails")
        pos("com.picadelic.fxguru", SDCARD, "FxGuru/thumbnails/$rngString")
        confirm(create())
    }

}