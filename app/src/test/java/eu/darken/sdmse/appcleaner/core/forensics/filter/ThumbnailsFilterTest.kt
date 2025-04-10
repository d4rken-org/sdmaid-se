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
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test hidden cache defaults`() = runTest {
        addDefaultNegatives()

        neg(testPkg, SDCARD, "$testPkg/.thumbnails")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.thumbnails")
        neg(testPkg, PRIVATE_DATA, "$testPkg/.thumbnails")

        pos(testPkg, SDCARD, "$testPkg/.thumbnails/file")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.thumbnails/file")
        pos(testPkg, PRIVATE_DATA, "$testPkg/.thumbnails/file")

        neg(testPkg, SDCARD, "$testPkg/files/.thumbnails")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.thumbnails")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/.thumbnails")

        pos(testPkg, SDCARD, "$testPkg/files/.thumbnails/file")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.thumbnails/file")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.thumbnails/file")
        
        confirm(create())
    }

    @Test fun `test quickoffice filter`() = runTest {
        addDefaultNegatives()
        neg("com.quickoffice.android", PRIVATE_DATA, "com.quickoffice.android/files")
        neg("com.quickoffice.android", PRIVATE_DATA, "com.quickoffice.android/files/asdasd")
        pos("com.quickoffice.android", PRIVATE_DATA, "com.quickoffice.android/files/kljalskdjlkjasd.thumb.panel")
        pos("com.quickoffice.android", PRIVATE_DATA, "com.quickoffice.android/files/kljalskdjlkjasd.thumb.home")
        confirm(create())
    }

    @Test fun `test nextapp fx filter`() = runTest {
        addDefaultNegatives()
        neg("nextapp.fx", SDCARD, ".FX/CloudThumbnail")
        pos("nextapp.fx", SDCARD, ".FX/CloudThumbnail/$rngString")
        neg("nextapp.fx", SDCARD, ".FX/ImageThumbnail")
        pos("nextapp.fx", SDCARD, ".FX/ImageThumbnail/$rngString")
        confirm(create())
    }

    @Test fun `test infzm reader filter`() = runTest {
        addDefaultNegatives()
        neg("net.coollet.infzmreader", SDCARD, "infzm/thumb_image")
        pos("net.coollet.infzmreader", SDCARD, "infzm/thumb_image/$rngString")
        confirm(create())
    }

    @Test fun `test kascend video filter`() = runTest {
        addDefaultNegatives()
        neg("com.kascend.video", SDCARD, "kascend/videoshow/.thumbcache")
        pos("com.kascend.video", SDCARD, "kascend/videoshow/.thumbcache/$rngString")
        confirm(create())
    }

    @Test fun `test walloid filter`() = runTest {
        addDefaultNegatives()
        val pkgs = listOf("com.hashcode.walloidpro", "com.hashcode.walloid")
        pkgs.forEach { pkg ->
            neg(pkg, SDCARD, "Walloid/.Thumbnail")
            pos(pkg, SDCARD, "Walloid/.Thumbnail/$rngString")
        }
        confirm(create())
    }

    @Test fun `test video player`() = runTest {
        neg("com.sec.android.app.videoplayer", SDCARD, ".thumbnails")
        pos("com.sec.android.app.videoplayer", SDCARD, ".thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `test samsung my files thumbnails`() = runTest {
        neg("com.sec.android.app.myfiles", SDCARD, "Movies/.thumbnails")
        pos("com.sec.android.app.myfiles", SDCARD, "Movies/.thumbnails/something")
        confirm(create())
    }

    @Test fun `test miui player`() = runTest {
        addDefaultNegatives()
        neg("com.miui.player", SDCARD, "MIUI/music/album/.player_thumb")
        pos("com.miui.player", SDCARD, "MIUI/music/album/.player_thumb/file")
        confirm(create())
    }

    @Test fun `test miui video thumbs`() = runTest {
        addDefaultNegatives()
        neg("com.miui.videoplayer", SDCARD, "MIUI/Video/thumb/")
        pos("com.miui.videoplayer", SDCARD, "MIUI/Video/thumb/6ba49cfe32916e890491ee101f97424d.thumb")
        confirm(create())
    }

    @Test fun `test movie fx`() = runTest {
        neg("tv.waterston.movieridefx", SDCARD, "MovieRideFX/thumbs")
        pos("tv.waterston.movieridefx", SDCARD, "MovieRideFX/thumbs/test")
        confirm(create())
    }

    @Test fun `Viber thumbs`() = runTest {
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/User photos/.thumbnails")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/User photos/.thumbnails/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/User photos/.thumbnails/$rngString")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.thumbnails")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.thumbnails/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `FxGuru thumbs`() = runTest {
        neg("com.picadelic.fxguru", SDCARD, "FxGuru/thumbnails")
        pos("com.picadelic.fxguru", SDCARD, "FxGuru/thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `rockon thumbs`() = runTest {
        neg("org.abrantix.rockon.rockonnggl", SDCARD, "albumthumbs")
        pos("org.abrantix.rockon.rockonnggl", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `poweramp thumbs`() = runTest {
        neg("com.maxmpz.audioplayer", SDCARD, "albumthumbs")
        pos("com.maxmpz.audioplayer", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `aag thumbs`() = runTest {
        neg("com.citc.aag", SDCARD, "albumthumbs")
        pos("com.citc.aag", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `blackplayer thumbs`() = runTest {
        neg("com.musicplayer.blackplayerfree", SDCARD, "albumthumbs")
        pos("com.musicplayer.blackplayerfree", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `another music player thumbs`() = runTest {
        neg("another.music.player", SDCARD, "albumthumbs")
        pos("another.music.player", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `Camera thumbs`() = runTest {
        neg("com.sec.android.app.camera", SDCARD, "DCIM/Camera/.thumbnails")
        pos("com.sec.android.app.camera", SDCARD, "DCIM/Camera/.thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `pulsar thumbs`() = runTest {
        neg("com.rhmsoft.pulsar", SDCARD, "albumthumbs")
        pos("com.rhmsoft.pulsar", SDCARD, "albumthumbs/$rngString")

        neg("com.rhmsoft.pulsar.pro", SDCARD, "albumthumbs")
        pos("com.rhmsoft.pulsar.pro", SDCARD, "albumthumbs/$rngString")
        confirm(create())
    }

    @Test fun `dont match default caches`() = runTest {
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/cache/User photos/.thumbnails/$rngString")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/Cache/.thumbnails/$rngString")

        neg("com.viber.voip", PRIVATE_DATA, "com.viber.voip/cache/User photos/.thumbnails/$rngString")
        neg("com.viber.voip", PRIVATE_DATA, "com.viber.voip/Cache/.thumbnails/$rngString")
        confirm(create())
    }

    @Test fun `SHAREit thumbs`() = runTest {
        val pkgs = setOf(
            "com.lenovo.anyshare.gps",
            "com.lenovo.anyshare",
            "shareit.lite",
            "shareit.premium",
        )
        pkgs.forEach {
            neg(it, PUBLIC_DATA, "$it/files/SHAREit/.thumbnails")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit/.thumbnails/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.thumbnails")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.thumbnails/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.thumbnails")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.thumbnails/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit/.mediathumbs")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit/.mediathumbs/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.mediathumbs")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.mediathumbs/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.mediathumbs")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.mediathumbs/anything")
            neg(it, SDCARD, "SHAREit/.thumbnails")
            pos(it, SDCARD, "SHAREit/.thumbnails/anything")
            neg(it, SDCARD, "SHAREit Lite/.thumbnails")
            pos(it, SDCARD, "SHAREit Lite/.thumbnails/anything")
            neg(it, SDCARD, "SHAREit Premium/.thumbnails")
            pos(it, SDCARD, "SHAREit Premium/.thumbnails/anything")
            neg(it, SDCARD, "SHAREit/.mediathumbs")
            pos(it, SDCARD, "SHAREit/.mediathumbs/anything")
            neg(it, SDCARD, "SHAREit Lite/.mediathumbs")
            pos(it, SDCARD, "SHAREit Lite/.mediathumbs/anything")
            neg(it, SDCARD, "SHAREit Premium/.mediathumbs")
            pos(it, SDCARD, "SHAREit Premium/.mediathumbs/anything")
        }
        confirm(create())
    }
}