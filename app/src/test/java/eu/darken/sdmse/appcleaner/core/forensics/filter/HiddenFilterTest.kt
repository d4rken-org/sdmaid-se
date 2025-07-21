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
import java.util.UUID

class HiddenFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = HiddenFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        environment = storageEnvironment,
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test hidden cache defaults`() = runTest {
        addDefaultNegatives()
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files")
        neg(setOf(testPkg), setOf(SDCARD, PRIVATE_DATA), "$testPkg/")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/video-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.image-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/video-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.video-cache")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/UnityCache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/_cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/Cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.Cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/video-cache/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/UnityCache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/_cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/Cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.Cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.image-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/video-cache/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.video-cache/file")
        pos(
            setOf(testPkg),
            setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA),
            "$testPkg/files/video-cache/LmWFkb0wDc9JIjEeQyYLRdQanDA"
        )
        pos(
            setOf(testPkg),
            setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA),
            "$testPkg/files/video-cache/j1ZyoUdWHQxeQ1JzBvzM3_68bn4/0000000000082000"
        )
        neg(
            setOf(testPkg),
            setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA),
            "$testPkg/files/UnityCache/j1ZyoUdWHQxeQ1JzBvzM3_68bn4/0000000000082000"
        )
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/TempData")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/TempData/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/TempData")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/TempData/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.temp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.temp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.temp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.temp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/temp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/temp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/temp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/temp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/tmp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/tmp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/tmp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/tmp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.tmp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.tmp/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.tmp")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.tmp/file")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/cache.dat")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/cache.dat")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image_cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image_cache/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image_cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image_cache/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.trash")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.trash/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.trash")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.trash/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.Trash")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.Trash/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.Trash")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.Trash/file")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/_cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/Cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.Cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/-cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image-cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/video-cache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/UnityCache/.nomedia")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/diskcache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/diskcache/something")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk-cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk-cache/something")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk_cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk_cache/something")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.diskcache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.diskcache/something")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk-cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk-cache/something")
        neg(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk_cache")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk_cache/something")

        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/-caches/something")

        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/imagecaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/imagecaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/image_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/image_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.imagecaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.imagecaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.image-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.image-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.image_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.image_caches/something")

        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/videocaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/videocaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/video-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/video-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/video_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/video_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.videocaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.videocaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.video-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.video-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.video_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.video_caches/something")

        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/mediacaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/mediacaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/media-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/media-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/media_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/media_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.mediacaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.mediacaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.media-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.media-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.media_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.media_caches/something")

        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/diskcaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/diskcaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/disk-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/disk_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/disk_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.diskcaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.diskcaches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.disk-caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/files/.disk_caches/something")
        pos(setOf(testPkg), setOf(SDCARD, PUBLIC_DATA, PRIVATE_DATA), "$testPkg/.disk_caches/something")

        confirm(create())
    }

    @Test fun `test filter sdm test`() = runTest {
        addDefaultNegatives()
        neg("eu.thedarken.sdm.test", PUBLIC_DATA, "com.soundcloud.android/files/skippy")
        pos("eu.thedarken.sdm.test", SDCARD, "sdm_test_file_cache_v2")
        pos("eu.thedarken.sdm.test", SDCARD, "sdm_test_file_cache1")
        pos("eu.thedarken.sdm.test", PUBLIC_DATA, "sdm_test_file_cache2")
        pos("eu.thedarken.sdm.test", PRIVATE_DATA, "sdm_test_file_cache3")
        pos("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/sdm_test_internal_hidden_cache_direct")
        pos("eu.thedarken.sdm.test", PUBLIC_DATA, "eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/$rngString")
        pos("eu.thedarken.sdm.test", PRIVATE_DATA, "eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/$rngString")
        pos(
            "eu.thedarken.sdm.test",
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/${UUID.randomUUID()}/$rngString"
        )
        confirm(create())
    }

    @Test fun `test filter soundcloud`() = runTest {
        addDefaultNegatives()
        neg("com.soundcloud.android", PUBLIC_DATA, "com.soundcloud.android/files/skippy")
        pos("com.soundcloud.android", PUBLIC_DATA, "com.soundcloud.android/files/skippy/$rngString")
        confirm(create())
    }

    @Test fun `test filter aptoide`() = runTest {
        addDefaultNegatives()
        neg("cm.aptoide.pt", SDCARD, ".aptoide/icons")
        pos("cm.aptoide.pt", SDCARD, ".aptoide/icons/$rngString")
        neg("cm.aptoide.pt", SDCARD, ".aptoide/apks")
        pos("cm.aptoide.pt", SDCARD, ".aptoide/apks/com.pandora.android.5.2.apk")
        pos("en.aptoide.com", SDCARD, ".aptoide/apks/com.pandora.android.5.2.apk")
        confirm(create())
    }

    @Test fun `test filter google camera`() = runTest {
        addDefaultNegatives()
        neg(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/TEMP_SESSIONS"
        )
        pos(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/TEMP_SESSIONS/PANO_455796234_5545668855"
        )
        neg(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/refocus"
        )
        pos(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/refocus/IMG_12547896_698745214"
        )
        neg(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/panorama_sessions"
        )
        pos(
            "com.google.android.GoogleCamera",
            PUBLIC_DATA,
            "com.google.android.GoogleCamera/files/panorama_sessions/session_56987415_43214845"
        )
        confirm(create())
    }

    @Test fun `test filter firefox`() = runTest {
        addDefaultNegatives()
        neg(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/Cache"
        )
        pos(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/Cache/$rngString"
        )
        neg(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox/files/mozilla/sqqj1c1o.default/Cache"
        )
        pos(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox/files/mozilla/sqqj1c1o.default/Cache/$rngString"
        )
        neg(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox/app_tmpdir/"
        )
        pos(
            setOf("org.mozilla.firefox", "org.mozilla.firefox_beta"),
            PRIVATE_DATA,
            "org.mozilla.firefox/app_tmpdir/$rngString"
        )
        confirm(create())
    }

    @Test fun `test mozilla fenix`() = runTest {
        addDefaultNegatives()
        neg("org.mozilla.fenix", PRIVATE_DATA, "org.mozilla.fenix/app_tmpdir/")
        pos("org.mozilla.fenix", PRIVATE_DATA, "org.mozilla.fenix/app_tmpdir/$rngString")
        confirm(create())
    }

    @Test fun `test filter genie widget`() = runTest {
        addDefaultNegatives()
        neg(
            "com.google.android.apps.genie.geniewidget",
            PRIVATE_DATA,
            "com.google.android.apps.genie.geniewidget/app_news_image_cache"
        )
        pos(
            "com.google.android.apps.genie.geniewidget",
            PRIVATE_DATA,
            "com.google.android.apps.genie.geniewidget/app_news_image_cache/abcdefg123456"
        )
        confirm(create())
    }

    @Test fun `test beautiful widgets`() = runTest {
        addDefaultNegatives()
        neg(
            setOf("com.levelup.beautifulwidgets", "com.levelup.beautifulwidgets.free"),
            PUBLIC_DATA,
            "com.levelup.beautifulwidgets/files/Pictures"
        )
        pos(
            setOf("com.levelup.beautifulwidgets", "com.levelup.beautifulwidgets.free"),
            PUBLIC_DATA,
            "com.levelup.beautifulwidgets/files/Pictures/91239123acbbea_540_rounded_top.png"
        )
        confirm(create())
    }

    @Test fun testFilterFrostWire() = runTest {
        addDefaultNegatives()
        neg("com.frostwire.android", SDCARD, "FrostWire/.image_cache")
        pos("com.frostwire.android", SDCARD, "FrostWire/.image_cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterWallbase() = runTest {
        addDefaultNegatives()
        neg("com.citc.wallbase", SDCARD, "Wallbase/.WallbaseCache")
        pos("com.citc.wallbase", SDCARD, "Wallbase/.WallbaseCache/$rngString")
        confirm(create())
    }

    @Test fun testFilterFacebookKatana() = runTest {
        addDefaultNegatives()
        neg("com.facebook.katana", SDCARD, "com.facebook.katana/fb_temp")
        pos("com.facebook.katana", SDCARD, "com.facebook.katana/fb_temp/.facebook_455866544.jpg")
        neg("com.facebook.katana", PRIVATE_DATA, "com.facebook.katana/files/video-cache")
        pos("com.facebook.katana", PRIVATE_DATA, "com.facebook.katana/files/video-cache/$rngString")
        neg("com.facebook.katana", PRIVATE_DATA, "com.facebook.katana/files/ExoPlayerCacheDir")
        pos("com.facebook.katana", PRIVATE_DATA, "com.facebook.katana/files/ExoPlayerCacheDir/$rngString")
        neg("com.facebook.katana", SDCARD, ".facebook_cache")
        pos("com.facebook.katana", SDCARD, ".facebook_cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterFacebookOcra() = runTest {
        addDefaultNegatives()
        neg("com.facebook.orca", SDCARD, "com.facebook.orca/fb_temp")
        pos("com.facebook.orca", SDCARD, "com.facebook.orca/fb_temp/.facebook_455866544.jpg")
        neg("com.facebook.orca", PRIVATE_DATA, "com.facebook.orca/files/ExoPlayerCacheDir")
        pos("com.facebook.orca", PRIVATE_DATA, "com.facebook.orca/files/ExoPlayerCacheDir/$rngString")
        neg("com.facebook.orca", SDCARD, ".facebook_cache")
        pos("com.facebook.orca", SDCARD, ".facebook_cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterAntutuGL2() = runTest {
        addDefaultNegatives()
        neg(
            "com.antutu.ABenchMark.GL2",
            PUBLIC_DATA,
            "com.antutu.ABenchMark.GL2/files/shader"
        )
        pos(
            "com.antutu.ABenchMark.GL2",
            PUBLIC_DATA,
            "com.antutu.ABenchMark.GL2/files/shader/$rngString"
        )
        confirm(create())
    }

    @Test fun testFilterDoodleJump() = runTest {
        addDefaultNegatives()
        neg("com.lima.doodlejump", PUBLIC_DATA, "com.lima.doodlejump/files/cache")
        pos("com.lima.doodlejump", PUBLIC_DATA, "com.lima.doodlejump/files/cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterAntutu() = runTest {
        addDefaultNegatives()
        neg(
            setOf("com.antutu.ABenchMark", "com.qqfriends.com.music"),
            SDCARD,
            ".antutu/benchmark/avatars"
        )
        pos(
            setOf("com.antutu.ABenchMark", "com.qqfriends.com.music"),
            SDCARD,
            ".antutu/benchmark/avatars/$rngString"
        )
        confirm(create())
    }

    @Test fun testFilterAudials() = runTest {
        addDefaultNegatives()
        neg(setOf("com.audials", "com.audials.paid"), SDCARD, "Audials/temporary/")
        pos(setOf("com.audials", "com.audials.paid"), SDCARD, "Audials/temporary/$rngString")
        confirm(create())
    }

    @Test fun testFilterAlicoid() = runTest {
        addDefaultNegatives()
        neg("com.magine.aliceoid", PRIVATE_DATA, "com.magine.aliceoid/data/images")
        pos("com.magine.aliceoid", PRIVATE_DATA, "com.magine.aliceoid/data/images/12345-67890")
        confirm(create())
    }

    @Test fun testFilterFlightRadar() = runTest {
        addDefaultNegatives()
        neg("com.flightradar24pro", SDCARD, "FlightRadar24/Alerts")
        pos("com.flightradar24pro", SDCARD, "FlightRadar24/Alerts/1234567890")
        confirm(create())
    }

    @Test fun testFilterPolarisOffice() = runTest {
        addDefaultNegatives()
        val pkgs = setOf(
            "com.infraware.polarisoffice",
            "com.infraware.polarisoffice5.entbiz.symantec",
            "com.infraware.polarisoffice.entbiz.citrix",
            "com.infraware.polarisoffice.entbiz.gd.viewer",
            "com.infraware.office.link",
            "com.infraware.polarisoffice4",
            "com.infraware.polarisoffice.entbiz.gd",
            "com.infraware.polarisoffice5tablet"
        )
        neg(pkgs, SDCARD, ".polaris_temp")
        pos(pkgs, SDCARD, ".polaris_temp/$rngString")
        confirm(create())
    }

    @Test fun testFilterTurboClient() = runTest {
        addDefaultNegatives()
        neg(setOf("turbo.client.free", "turbo.client"), SDCARD, "Turbo Client/temp")
        pos(setOf("turbo.client.free", "turbo.client"), SDCARD, "Turbo Client/temp/$rngString")
        confirm(create())
    }

    @Test fun testFilterYahooMail() = runTest {
        addDefaultNegatives()
        neg("com.yahoo.mobile.client.android.mail", SDCARD, "yahoo/mail/imgCache")
        pos("com.yahoo.mobile.client.android.mail", SDCARD, "yahoo/mail/imgCache/$rngString")
        confirm(create())
    }

    @Test fun testFilterLGEClipTray() = runTest {
        addDefaultNegatives()
        neg("com.lge.software.cliptray", PUBLIC_DATA, ".cliptray")
        neg("com.lge.software.cliptray", PUBLIC_DATA, ".cliptray/.nomedia")
        pos("com.lge.software.cliptray", PUBLIC_DATA, ".cliptray/$rngString")
        confirm(create())
    }

    @Test fun testFilterEFile() = runTest {
        addDefaultNegatives()
        neg("com.domobile.efile", SDCARD, ".eFile_trash")
        pos("com.domobile.efile", SDCARD, ".eFile_trash/$rngString")
        confirm(create())
    }

    @Test fun testFilterTencentMovieTicket() = runTest {
        addDefaultNegatives()
        neg("com.tencent.movieticket", SDCARD, ".QQMovieTicket/.cache")
        pos("com.tencent.movieticket", SDCARD, ".QQMovieTicket/.cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterSohuVideo() = runTest {
        addDefaultNegatives()
        neg("com.sohu.sohuvideo", PUBLIC_DATA, "com.sohu.sohuvideo/tempVideo")
        pos("com.sohu.sohuvideo", PUBLIC_DATA, "com.sohu.sohuvideo/tempVideo/$rngString")
        confirm(create())
    }

    @Test fun testFilterTencentLbsCheckin() = runTest {
        addDefaultNegatives()
        neg("com.tencent.lbs.checkin", SDCARD, ".lbsGo/avatar_cache")
        pos("com.tencent.lbs.checkin", SDCARD, ".lbsGo/avatar_cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterWuba() = runTest {
        addDefaultNegatives()
        neg("com.wuba.jiaoyou", SDCARD, "wuba/jiaoyou/casheimage")
        pos("com.wuba.jiaoyou", SDCARD, "wuba/jiaoyou/casheimage/$rngString")
        confirm(create())
    }

    @Test fun testFilterEverNoteSkitch() = runTest {
        addDefaultNegatives()
        neg("com.evernote.skitch", SDCARD, "Skitch/Temp")
        pos("com.evernote.skitch", SDCARD, "Skitch/Temp/$rngString")
        confirm(create())
    }

    @Test fun testFilterAutoNavi() = runTest {
        addDefaultNegatives()
        neg(
            "com.telenav.doudouyou.android.autonavi",
            PUBLIC_DATA,
            "com.telenav.doudouyou.android.autonavi/cacheimage"
        )
        pos(
            "com.telenav.doudouyou.android.autonavi",
            PUBLIC_DATA,
            "com.telenav.doudouyou.android.autonavi/cacheimage/$rngString"
        )
        confirm(create())
    }

    @Test fun testFilterYahooWeather() = runTest {
        addDefaultNegatives()
        neg("com.yahoo.mobile.client.android.weather", SDCARD, "yahoo/weather/imgCache")
        pos("com.yahoo.mobile.client.android.weather", SDCARD, "yahoo/weather/imgCache/$rngString")
        confirm(create())
    }

    @Test fun testFilterBahnMultiCity() = runTest {
        addDefaultNegatives()
        neg("de.bahn.multicity", SDCARD, "cache")
        pos("de.bahn.multicity", SDCARD, "cache/mc.json")
        confirm(create())
    }

    @Test fun testFilterPerfectScreenshot() = runTest {
        addDefaultNegatives()
        neg(
            setOf("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot"),
            SDCARD,
            "Pictures/perfectscreenshot_tmp"
        )
        neg(
            setOf("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot"),
            SDCARD,
            "Pictures/perfectscreenshot_tmp/.nomedia"
        )
        pos(
            setOf("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot"),
            SDCARD,
            "Pictures/perfectscreenshot_tmp/$rngString"
        )
        confirm(create())
    }

    @Test fun testFilterCleanMasterGuard() = runTest {
        addDefaultNegatives()
        neg("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/cache")
        pos("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/cache/$rngString")
        neg("com.cleanmaster.mguard", PRIVATE_DATA, "com.cleanmaster.mguard/files/cache")
        pos("com.cleanmaster.mguard", PRIVATE_DATA, "com.cleanmaster.mguard/files/cache/$rngString")
        neg("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/iconcache")
        pos("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/iconcache/$rngString")
        confirm(create())
    }

    @Test fun testFilterMagix() = runTest {
        addDefaultNegatives()
        neg("com.magix.camera_mx", SDCARD, "Magix/.tmp")
        pos("com.magix.camera_mx", SDCARD, "Magix/.tmp/$rngString")
        confirm(create())
    }

    @Test fun testFilterFlipboard() = runTest {
        addDefaultNegatives()
        neg("flipboard.app", PUBLIC_DATA, "flipboard.app/files/cache")
        pos("flipboard.app", PUBLIC_DATA, "flipboard.app/files/cache/$rngString")
        confirm(create())
    }

    @Test fun testFilterNineGag() = runTest {
        addDefaultNegatives()
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/images")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/images/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/images/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags_thumb")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags_thumb/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gags_thumb/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gifs")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gifs/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/gifs/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/mp4s")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/mp4s/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/mp4s/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/uploads")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/uploads/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/uploads/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/avatar")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/avatar/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/avatar/$rngString")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/covers")
        neg("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/covers/.nomedia")
        pos("com.ninegag.android.app", PUBLIC_DATA, "com.ninegag.android.app/files/covers/$rngString")
        confirm(create())
    }

    @Test fun testFilterAmazonMusic() = runTest {
        addDefaultNegatives()
        neg(setOf("com.amazon.mp3", "com.amazon.bueller.music"), SDCARD, "amazonmp3/temp")
        pos(setOf("com.amazon.mp3", "com.amazon.bueller.music"), SDCARD, "amazonmp3/temp/$rngString")
        confirm(create())
    }

    @Test fun testFilterSimplePlanes() = runTest {
        addDefaultNegatives()
        neg("com.jundroo.SimplePlanes", SDCARD, ".EveryplayCache/com.jundroo.SimplePlanes")
        pos("com.jundroo.SimplePlanes", SDCARD, ".EveryplayCache/com.jundroo.SimplePlanes/$rngString")
        confirm(create())
    }

    @Test fun testFilterPolarisViewer() = runTest {
        addDefaultNegatives()
        neg("com.infraware.polarisviewer4", SDCARD, ".PolarisViewer4/polarisTemp")
        pos("com.infraware.polarisviewer4", SDCARD, ".PolarisViewer4/polarisTemp/$rngString")
        confirm(create())
    }

    @Test fun testFilterSlingPlayer() = runTest {
        addDefaultNegatives()
        val pkgs = setOf(
            "com.slingmedia.slingPlayer",
            "com.slingmedia.slingPlayerTablet",
            "com.slingmedia.slingPlayerTabletFreeApp",
            "com.slingmedia.slingPlayerFreeApp"
        )
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayer/files/tsDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayer/files/logDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTablet/files/tsDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTablet/files/logDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTabletFreeApp/files/tsDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTabletFreeApp/files/logDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerFreeApp/files/tsDump")
        neg(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerFreeApp/files/logDump")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayer/files/tsDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayer/files/logDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTablet/files/tsDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTablet/files/logDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTabletFreeApp/files/tsDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerTabletFreeApp/files/logDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerFreeApp/files/tsDump/$rngString")
        pos(pkgs, PUBLIC_DATA, "com.slingmedia.slingPlayerFreeApp/files/logDump/$rngString")
        confirm(create())
    }

    @Test fun testFilterStockGallery() = runTest {
        addDefaultNegatives()
        neg("com.sec.android.gallery3d", PRIVATE_DATA, "com.sec.android.gallery3d")
        neg("com.sec.android.gallery3d", PRIVATE_DATA, "com.sec.android.gallery3d/Temp")
        pos("com.sec.android.gallery3d", PRIVATE_DATA, "com.sec.android.gallery3d/Temp/1456180349484")
        confirm(create())
    }

    @Test fun testFilterSkype() = runTest {
        addDefaultNegatives()
        val pkgs = setOf("com.skype.raider", "com.skype.rover")
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/media_cache"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/media_cache_v2"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/emo_cache"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/emo_cache_v2"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/media_cache"
        )
        neg(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/media_cache_v2"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/media_cache/asyncdb/cache_db.db"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.rover/files/darkenlaptop/media_messaging/media_cache_v3/asyncdb/cache_db.db"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/emo_cache/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/emo_cache/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/media_cache/asyncdb/cache_db.db"
        )
        pos(
            pkgs,
            PRIVATE_DATA,
            "com.skype.raider/files/darkenlaptop/media_messaging/media_cache_v3/asyncdb/cache_db.db"
        )
        confirm(create())
    }

    @Test fun `test es file explorer`() = runTest {
        val pkgs = setOf(
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.cupcake",
            "com.estrongs.android.pop.app.shortcut",
            "com.estrongs.android.pop.pro"
        )
        pos(pkgs, SDCARD, ".estrongs/dianxin/notify/.cache/" + UUID.randomUUID())
        neg(pkgs, SDCARD, ".estrongs/dianxin/notify/.cache")
        pos(pkgs, SDCARD, "dianxin/notify/.cache/" + UUID.randomUUID())
        neg(pkgs, SDCARD, "dianxin/notify/.cache")
        neg(pkgs, SDCARD, ".estrongs/recycle/" + UUID.randomUUID())
        neg(pkgs, SDCARD, ".estrongs/recycle")
        pos(pkgs, SDCARD, ".BD_SAPI_CACHE/" + UUID.randomUUID())
        neg(pkgs, SDCARD, ".BD_SAPI_CACHE")
        neg(pkgs, SDCARD, "BD_SAPI_CACHE")
        neg(pkgs, SDCARD, ".BD_SAPI")
        neg(pkgs, SDCARD, ".estrongs/.app_icon_back")
        pos(pkgs, SDCARD, ".estrongs/.app_icon_back/strawberrycake")
        confirm(create())
    }

    @Test fun `test cm launcher`() = runTest {
        pos("com.ksmobile.launcher", PUBLIC_DATA, "com.ksmobile.launcher/files/iconcache/" + UUID.randomUUID())
        neg("com.ksmobile.launcher", PUBLIC_DATA, "com.ksmobile.launcher/files/iconcache")
        confirm(create())
    }

    @Test fun `test asus webstorage`() = runTest {
        pos(
            "com.ecareme.asuswebstorage",
            PUBLIC_DATA,
            "com.ecareme.asuswebstorage/folderBrowseCache/" + UUID.randomUUID()
        )
        neg(
            "com.ecareme.asuswebstorage",
            PUBLIC_DATA,
            "com.ecareme.asuswebstorage/folderBrowseCache"
        )
        confirm(create())
    }

    @Test fun `test everyplay app cache`() = runTest {
        pos(
            "com.everyplay.everyplayapp",
            SDCARD,
            ".EveryplayCache/images/" + UUID.randomUUID()
        )
        neg(
            "com.everyplay.everyplayapp",
            SDCARD,
            ".EveryplayCache/com.some.package/" + UUID.randomUUID()
        )
        neg(
            "com.everyplay.everyplayapp",
            SDCARD,
            ".EveryplayCache/images" + "com.everyplay.everyplayapp"
        )
        confirm(create())
    }

    @Test fun `test face folder`() = runTest {
        pos(
            setOf("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d"),
            SDCARD,
            ".face/facedata"
        )
        pos(
            setOf("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d"),
            SDCARD,
            ".face/11111"
        )
        neg(
            setOf("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d"),
            SDCARD,
            ".face"
        )
        confirm(create())
    }

    @Test fun `test amazon market downloaded apks`() = runTest {
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/files/update-1-1.apk"
        )
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/files/update-12-123456789.apk"
        )
        neg(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/files"
        )
        neg(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/files/some.apk"
        )
        neg(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/files/update-1-12346789.zip"
        )
        neg(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "some.other.app/files/update-1-1.apk"
        )
        pos(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "com.amazon.mShop.android/files/apks/update-1-1.apk"
        )
        pos(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "com.amazon.mShop.android/files/apks/update-12-123456789.apk"
        )
        neg(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "com.amazon.mShop.android/files/apks"
        )
        neg(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "com.amazon.mShop.android/files/apks/some.apk"
        )
        neg(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "com.amazon.mShop.android/files/apks/update-1-12346789.zip"
        )
        neg(
            "com.amazon.mShop.android",
            PUBLIC_DATA,
            "some.other.app/files/apks/update-1-1.apk"
        )
        confirm(create())
    }

    @Test fun testKate() = runTest {
        val pkgs = setOf(
            "com.perm.kate",
            "com.perm.kate.pro",
            "com.perm.kate_new_2"
        )
        pos(pkgs, SDCARD, ".Kate/image_cache/" + UUID.randomUUID())
        pos(pkgs, SDCARD, ".Kate/image_cache/-113291110_-2052025531")
        neg(pkgs, PUBLIC_DATA, ".Kate/image_cache")
        confirm(create())
    }

    @Test fun `test plan tronics find my headset`() = runTest {
        pos("com.plantronics.findmyheadset", SDCARD, "cabotEL.log")
        neg("com.plantronics.findmyheadset", SDCARD, "cabotel")
        confirm(create())
    }

    @Test fun `test motions stils`() = runTest {
        pos("com.google.android.apps.motionstills", SDCARD, "motionstills/warp_grid_cache/test")
        neg("com.google.android.apps.motionstills", SDCARD, "motionstills/warp_grid_cache/.nomedia")
        neg("com.google.android.apps.motionstills", SDCARD, "motionstills/warp_grid_cache")
        neg("com.google.android.apps.motionstills", SDCARD, "motionstills")
        confirm(create())
    }

    @Test fun `test plex media server`() = runTest {
        pos("com.plexapp.android", PRIVATE_DATA, "com.plexapp.android/Plex Media Server/Cache/test")
        neg("com.plexapp.android", PRIVATE_DATA, "com.plexapp.android/Plex Media Server/Cache")
        confirm(create())
    }

    @Test fun testFotaDownload() = runTest {
        pos("com.tcl.ota.bb", SDCARD, ".fotadownload/test")
        neg("com.tcl.ota.bb", SDCARD, ".fotadownload")
        confirm(create())
    }

    @Test fun testBrowser() = runTest {
        pos("com.android.browser", SDCARD, "Browser/MediaCache/Test")
        neg("com.android.browser", SDCARD, "Browser/MediaCache")
        confirm(create())
    }

    @Test fun testUCDownloadsApolloCache() = runTest {
        val pkgs = setOf(
            "com.UCMobile",
            "com.UCMobile.intl",
            "com.UCMobile.x86",
            "com.UCMobile.xiaoma",
            "com.UCMobile.ac",
            "com.UCMobile.testch",
            "com.uc.browser.en",
            "com.uc.browser",
            "com.uc.browser.hd"
        )
        neg(pkgs, SDCARD, "UCDownloads/video/.apolloCache")
        pos(pkgs, SDCARD, "UCDownloads/video/.apolloCache/strawberry")
        neg(pkgs, SDCARD, "UCDownloadsPad/video/.apolloCache")
        pos(pkgs, SDCARD, "UCDownloadsPad/video/.apolloCache/strawberry")
        neg(pkgs, PUBLIC_DATA, "com.UCMobile.intl/files/UcDownloads/video/.apolloCache")
        pos(pkgs, PUBLIC_DATA, "com.UCMobile.intl/files/UcDownloads/video/.apolloCache/strawberry")
        neg(pkgs, SDCARD, "supercache")
        pos(pkgs, SDCARD, "supercache/test")
        confirm(create())
    }

    @Test fun `test chrome temp download file`() = runTest {
        neg("com.android.chrome", SDCARD, "Download")
        neg("com.android.chrome", SDCARD, "Download/.crdownload")
        neg("com.android.chrome", SDCARD, "$rngString.crdownload")
        pos("com.android.chrome", SDCARD, "Download/$rngString.crdownload")
        confirm(create())
    }

    @Test fun testMusically() = runTest {
        addDefaultNegatives()
        neg("com.zhiliaoapp.musically", PUBLIC_DATA, "com.zhiliaoapp.musically/files/frescocache")
        pos("com.zhiliaoapp.musically", PUBLIC_DATA, "com.zhiliaoapp.musically/files/frescocache/$rngString")
        neg("com.zhiliaoapp.musically", PUBLIC_DATA, "com.zhiliaoapp.musically/Videos")
        pos("com.zhiliaoapp.musically", PUBLIC_DATA, "com.zhiliaoapp.musically/Videos/$rngString")
        confirm(create())
    }

    @Test fun testSnapchat() = runTest {
        addDefaultNegatives()
        neg("com.snapchat.android", PRIVATE_DATA, "com.snapchat.android/files/media_cache")
        pos("com.snapchat.android", PRIVATE_DATA, "com.snapchat.android/files/media_cache/$rngString")
        confirm(create())
    }

    @Test fun testKeerby() = runTest {
        addDefaultNegatives()
        neg("com.keerby.formatfactory", SDCARD, "Keerby/FormatFactory/$rngString")
        pos("com.keerby.formatfactory", SDCARD, "Keerby/FormatFactory/tmp/$rngString")
        confirm(create())
    }

    @Test fun testGooglePlus() = runTest {
        addDefaultNegatives()
        neg(
            "com.google.android.apps.plus",
            setOf(PUBLIC_DATA, PRIVATE_DATA),
            "com.google.android.apps.plus/app_localMedia"
        )
        pos(
            "com.google.android.apps.plus",
            setOf(PUBLIC_DATA, PRIVATE_DATA),
            "com.google.android.apps.plus/app_localMedia/$rngString"
        )
        confirm(create())
    }

    @Test fun testZArchiver() = runTest {
        addDefaultNegatives()
        neg("ru.zdevs.zarchiver", PUBLIC_DATA, "ru.zdevs.zarchiver/files")
        pos("ru.zdevs.zarchiver", PUBLIC_DATA, "ru.zdevs.zarchiver/files/$rngString")
        confirm(create())
    }

    @Test fun testNaviKing() = runTest {
        addDefaultNegatives()
        neg("com.kingwaytek.naviking", SDCARD, "LocalKingMapTempN5")
        pos("com.kingwaytek.naviking", SDCARD, "LocalKingMapTempN5/$rngString")
        confirm(create())
    }

    @Test fun testGlock() = runTest {
        addDefaultNegatives()
        neg("com.genie9.glock", SDCARD, ".GLock/.cache")
        pos("com.genie9.glock", SDCARD, ".GLock/.cache/$rngString")
        confirm(create())
    }

    @Test fun testTCLUpdater() = runTest {
        addDefaultNegatives()
        neg("com.tcl.ota.bb", PUBLIC_DATA, ".fotaApps")
        pos("com.tcl.ota.bb", PUBLIC_DATA, ".fotaApps/$rngString")
        confirm(create())
    }

    @Test fun testWhatsAppShared() = runTest {
        addDefaultNegatives()
        neg("com.whatsapp", SDCARD, "WhatsApp/.Shared")
        pos("com.whatsapp", SDCARD, "WhatsApp/.Shared/$rngString")
        confirm(create())
    }

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2084
     * [GameFilesFilterTest.testUnity3dGameData]
     */
    @Test fun `test unity offline game data`() = runTest {
        addDefaultNegatives()
        neg("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986")
        neg("test.pkg", PUBLIC_DATA, "test.pkg/files/Cache/$rngString.unity3d&ux=$rngString")
        confirm(create())
    }

    @Test fun testXploreFileManager() = runTest {
        addDefaultNegatives()
        neg("com.lonelycatgames.Xplore", PUBLIC_DATA, "com.lonelycatgames.Xplore/files/Send Anywhere/.temp")
        pos("com.lonelycatgames.Xplore", PUBLIC_DATA, "com.lonelycatgames.Xplore/files/Send Anywhere/.temp/$rngString")
        confirm(create())
    }

    @Test fun `wechat tencent micro message`() = runTest {
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/CheckResUpdate")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/CheckResUpdate/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/WebviewCache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/WebviewCache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/Cache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/Cache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/.tmp")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/.tmp/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/CDNTemp")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/CDNTemp/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/FailMsgFileCache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/FailMsgFileCache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/diskcache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/diskcache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e63cd8c46008e42a3d3d614a3temp012345789111")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3temp0123456789111")
        neg("com.tencent.mm", SDCARD, "tencent/assistant/cache")
        pos("com.tencent.mm", SDCARD, "tencent/assistant/cache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/assistant/thumbnailcache")
        pos("com.tencent.mm", SDCARD, "tencent/assistant/thumbnailcache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/assistant/tmp")
        pos("com.tencent.mm", SDCARD, "tencent/assistant/tmp/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/emoji/cover")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/emoji/cover/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/mmslot/webcached")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/mmslot/webcached/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxacache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxacache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxafiles")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxafiles/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxanewfiles")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxanewfiles/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxvideocache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxvideocache/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxvideotmp")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/wxvideotmp/$rngString")

        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/CheckResUpdate")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/CheckResUpdate/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/WebviewCache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/WebviewCache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/Cache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/Cache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/.tmp")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/.tmp/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/CDNTemp")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/CDNTemp/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/FailMsgFileCache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/FailMsgFileCache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/diskcache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/diskcache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e63cd8c46008e42a3d3d614a3temp012345789111")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3temp0123456789111")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/cache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/cache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/thumbnailcache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/thumbnailcache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/tmp")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/assistant/tmp/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon")
        pos(
            "com.tencent.mm",
            PUBLIC_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon/$rngString"
        )
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download")
        pos(
            "com.tencent.mm",
            PUBLIC_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download/$rngString"
        )
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/emoji/cover")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/emoji/cover/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/mmslot/webcached")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/mmslot/webcached/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache")
        pos(
            "com.tencent.mm",
            PUBLIC_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache/$rngString"
        )
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxacache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxacache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxafiles")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxafiles/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxanewfiles")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxanewfiles/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxvideocache")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxvideocache/$rngString")
        neg("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxvideotmp")
        pos("com.tencent.mm", PUBLIC_DATA, "com.tencent.mm/MicroMsg/wxvideotmp/$rngString")

        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image/$rngString")
        neg(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/CheckResUpdate")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/CheckResUpdate/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/WebviewCache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/WebviewCache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/Cache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/Cache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/.tmp")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/.tmp/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/CDNTemp")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/CDNTemp/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/FailMsgFileCache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/FailMsgFileCache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/diskcache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/diskcache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e63cd8c46008e42a3d3d614a3temp012345789111")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3temp0123456789111")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/cache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/cache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/thumbnailcache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/thumbnailcache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/tmp")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/assistant/tmp/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon")
        pos(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/brandicon/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download")
        pos(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/cdn/download/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/emoji/cover")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/emoji/cover/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/mmslot/webcached")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/mmslot/webcached/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache")
        pos(
            "com.tencent.mm",
            PRIVATE_DATA,
            "com.tencent.mm/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/openapi_cache/$rngString"
        )
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxacache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxacache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxafiles")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxafiles/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxanewfiles")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxanewfiles/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxvideocache")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxvideocache/$rngString")
        neg("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxvideotmp")
        pos("com.tencent.mm", PRIVATE_DATA, "com.tencent.mm/MicroMsg/wxvideotmp/$rngString")

        neg("com.tencent.mm", SDCARD, "VideoCache/com.tencent.mm")
        pos("com.tencent.mm", SDCARD, "VideoCache/com.tencent.mm/$rngString")

        confirm(create())
    }

    @Test fun testNewsBreak() = runTest {
        addDefaultNegatives()
        neg("com.particlenews.newsbreak", SDCARD, ".newsbreak/image")
        pos("com.particlenews.newsbreak", SDCARD, ".newsbreak/image/$rngString")
        confirm(create())
    }

    @Test fun testSqlitePrime() = runTest {
        addDefaultNegatives()
        neg("com.lastempirestudio.sqliteprime", SDCARD, "SqlitePrime/cache")
        pos("com.lastempirestudio.sqliteprime", SDCARD, "SqlitePrime/cache/$rngString")
        confirm(create())
    }

    @Test fun testSems() = runTest {
        addDefaultNegatives()
        neg("com.samsung.android.mobileservice", SDCARD, ".sems")
        pos("com.samsung.android.mobileservice", SDCARD, ".sems/sa_groups_img_family_grid.png")
        confirm(create())
    }

    @Test fun testQPython() = runTest {
        addDefaultNegatives()
        neg("org.qpython.qpy", SDCARD, "qpython")
        neg("org.qpython.qpy", SDCARD, "qpython/projects")
        neg("org.qpython.qpy", SDCARD, "qpython/.notebook")
        neg("org.qpython.qpy", SDCARD, "qpython/lib/1thing")
        neg("org.qpython.qpy", SDCARD, "qpython/log")
        neg("org.qpython.qpy", SDCARD, "qpython/cache")
        pos("org.qpython.qpy", SDCARD, "qpython/log/1thing")
        pos("org.qpython.qpy", SDCARD, "qpython/cache/1thing")
        confirm(create())
    }

    @Test fun testVMOS() = runTest {
        addDefaultNegatives()
        neg("com.vmos.glb", SDCARD, "VMOSfiletransferstation")
        pos("com.vmos.glb", SDCARD, "VMOSfiletransferstation/something_!\"§$%&/()=?ÄÖ_:;'ÄÖ'")
        confirm(create())
    }

    @Test fun testSketchCode() = runTest {
        addDefaultNegatives()
        neg("com.sketch.code.two", SDCARD, ".sketchcode")
        neg("com.sketch.code.two", SDCARD, ".sketchcode/temp")
        pos("com.sketch.code.two", SDCARD, ".sketchcode/temp/something_!\"§$%&/()=?ÄÖ_:;'ÄÖ'")
        confirm(create())
    }

    @Test fun testVkontakte() = runTest {
        addDefaultNegatives()
        neg("com.vkontakte.android", SDCARD, ".vkontakte/something")
        pos("com.vkontakte.android", SDCARD, ".vkontakte/autoplay_gif_cache")
        confirm(create())
    }

    @Test fun testMagicVideoHiddenTmp() = runTest {
        addDefaultNegatives()
        neg("com.magicvideo.beauty.videoeditor", SDCARD, ".tmp")
        neg("com.magicvideo.beauty.videoeditor", SDCARD, ".tmp508890d1-5954-404a-a28f-01bbb8d5150")
        neg("com.magicvideo.beauty.videoeditor", SDCARD, ".tmp508890d1-5954-404a-a28f-01bbb8d5150ee")
        neg("com.magicvideo.beauty.videoeditor", SDCARD, ".tmp508890d1-5954-404a-a28f-01bbb8d5150G")
        confirm(create())
    }

    @Test fun `Likee app cache directories`() = runTest {
        addDefaultNegatives()
        neg("video.like", SDCARD, "video.like/nerv-cacheg")
        pos("video.like", SDCARD, "video.like/nerv-cache/something")
        neg("video.like", PUBLIC_DATA, "video.like/files/kk")
        pos("video.like", PUBLIC_DATA, "video.like/files/kk/something")
        neg("video.like", PUBLIC_DATA, "video.like/files/xlog")
        pos("video.like", PUBLIC_DATA, "video.like/files/xlog/test")
        confirm(create())
    }

    @Test fun `Luumi app cache directories`() = runTest {
        addDefaultNegatives()
        neg("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.cache")
        pos("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.cache/something")
        neg("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.tattooTemp")
        pos("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.tattooTemp/something")
        confirm(create())
    }

    @Test fun `Photo Editor Pro cache directories`() = runTest {
        addDefaultNegatives()
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.brush")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.brush/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.bg")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.bg/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.neon")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.neon/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.mosaic")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.mosaic/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.lightfx")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.lightfx/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.filter")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.filter/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.edited_photo")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.edited_photo/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.eraser")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.eraser/something")
        neg("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.sticker")
        pos("photo.editor.photoeditor.photoeditorpro", SDCARD, "Photo Editor/.sticker/something")
        confirm(create())
    }

    @Test fun `Auto Photo Cut Paste temp directory`() = runTest {
        addDefaultNegatives()
        neg("com.morningshine.autocutpaste", SDCARD, "DCIM/Auto Photo Cut Paste/.temp")
        pos("com.morningshine.autocutpaste", SDCARD, "DCIM/Auto Photo Cut Paste/.temp/something")
        confirm(create())
    }

    @Test fun `Gender Editor temp directory`() = runTest {
        addDefaultNegatives()
        neg("com.morningshine.autocutpaste", SDCARD, "GenderEditor/temp")
        pos("com.morningshine.autocutpaste", SDCARD, "GenderEditor/temp/something")
        confirm(create())
    }

    @Test fun `Guru Video Maker disk cache`() = runTest {
        addDefaultNegatives()
        neg("videoeditor.videomaker.videoeditorforyoutube", PUBLIC_DATA, "Video.Guru/.disk_cache")
        pos("videoeditor.videomaker.videoeditorforyoutube", PUBLIC_DATA, "Video.Guru/.disk_cache/something")
        confirm(create())
    }

    @Test fun `Magic Airbrush cache directory`() = runTest {
        addDefaultNegatives()
        neg("com.magicv.airbrush", SDCARD, "AirBrush/.cache")
        pos("com.magicv.airbrush", SDCARD, "AirBrush/.cache/something")
        confirm(create())
    }

    @Test fun `Body Editor cache directories`() = runTest {
        addDefaultNegatives()
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.cache")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.cache/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.bg")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.bg/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.filter")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.filter/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.font")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.font/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.log")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.log/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.sticker")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.sticker/something")
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.tattooTemp")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.tattooTemp/something")
        confirm(create())
    }

    @Test fun `Cut Paste Frames temp directory`() = runTest {
        addDefaultNegatives()
        neg("com.zmobileapps.cutpasteframes", SDCARD, "DCIM/Cut Paste Frames/.temp")
        pos("com.zmobileapps.cutpasteframes", SDCARD, "DCIM/Cut Paste Frames/.temp/something")
        confirm(create())
    }

    @Test fun `BeautyPlus cache directories`() = runTest {
        addDefaultNegatives()
        neg("com.commsource.beautyplus", SDCARD, "BeautyPlus/.videocache")
        pos("com.commsource.beautyplus", SDCARD, "BeautyPlus/.videocache/something")
        neg("com.commsource.beautyplus", SDCARD, "BeautyPlus/.temp")
        pos("com.commsource.beautyplus", SDCARD, "BeautyPlus/.temp/something")
        neg("com.commsource.beautyplus", SDCARD, "BeautyPlus/.cache")
        pos("com.commsource.beautyplus", SDCARD, "BeautyPlus/.cache/something")
        confirm(create())
    }

    @Test fun `AutoCutCut web images directory`() = runTest {
        addDefaultNegatives()
        neg("com.vyroai.AutoCutCut", SDCARD, "Pictures/something")
        neg("com.vyroai.AutoCutCut", SDCARD, "Pictures/.WebImages")
        pos("com.vyroai.AutoCutCut", SDCARD, "Pictures/.WebImages/something")
        confirm(create())
    }

    @Test fun `RemoveR temp directory`() = runTest {
        addDefaultNegatives()
        neg("remove.unwanted.object", SDCARD, "removertemp")
        pos("remove.unwanted.object", SDCARD, "removertemp/something")
        confirm(create())
    }

    @Test fun `B612 app directories`() = runTest {
        addDefaultNegatives()
        neg("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/music")
        neg("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/image")
        neg("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/filter")
        neg("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/sticker")
        pos("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/music/something")
        pos("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/image/something")
        pos("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/filter/something")
        pos("com.linecorp.b612.android", PUBLIC_DATA, "com.linecorp.b612.android/files/sticker/something")
        confirm(create())
    }

    @Test fun `ZCamera image cache`() = runTest {
        addDefaultNegatives()
        neg("com.jb.zcamera", SDCARD, "ZCamera/image/cache")
        pos("com.jb.zcamera", SDCARD, "ZCamera/image/cache/something")
        confirm(create())
    }

    @Test fun `Instagram Live temp files`() = runTest {
        addDefaultNegatives()
        neg("com.instagram.android", SDCARD, ".InstagramLive/")
        neg("com.instagram.android", SDCARD, ".InstagramLive/files")
        pos("com.instagram.android", SDCARD, ".InstagramLive/tmp_live_18025560862282799_thumb.jpg")
        confirm(create())
    }

    @Test fun `Viber icon caches`() = runTest {
        addDefaultNegatives()
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.icons")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.icons/.nomedia")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/aicons/programmist87")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.icons/programmist87")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/group_icons/7c4f99f8e294485811343b60e3a71cfb.jpg")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.group_icons")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.group_icons/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.group_icons/7c4f99f8e294485811343b60e3a71cfb.jpg")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.public_cache")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.public_cache/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.public_cache/7c4f99f8e294485811343b60e3a71cfb.jpg")
        confirm(create())
    }

    @Test fun `Viber temp files`() = runTest {
        addDefaultNegatives()
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.shsh/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.shsh/file")

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.temp/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.temp/file")
        confirm(create())
    }

    @Test fun `JustShot album cache`() = runTest {
        addDefaultNegatives()
        neg("com.ufotosoft.justshot", SDCARD, "AlbumCache")
        pos("com.ufotosoft.justshot", SDCARD, "AlbumCache/something")
        confirm(create())
    }

    @Test fun `2Accounts game resources`() = runTest {
        addDefaultNegatives()
        neg("com.excelliance.multiaccount", SDCARD, ".dygameres.apps/game_res/3rd/icon")
        pos("com.excelliance.multiaccount", SDCARD, ".dygameres.apps/game_res/3rd/icon/something")
        confirm(create())
    }

    @Test fun `MagiCut temp files`() = runTest {
        addDefaultNegatives()
        neg("com.energysh.onlinecamera1", SDCARD, "newone.pnga")
        neg("com.energysh.onlinecamera1", SDCARD, "newone.png/something")
        neg("com.energysh.onlinecamera1", SDCARD, "newone")
        pos("com.energysh.onlinecamera1", SDCARD, "newone.png")
        confirm(create())
    }

    @Test fun `ZBeautyCamera image cache`() = runTest {
        addDefaultNegatives()
        neg("com.jb.beautycam", SDCARD, "ZBeautyCamera/image/cache")
        pos("com.jb.beautycam", SDCARD, "ZBeautyCamera/image/cache/something")
        confirm(create())
    }

    @Test fun `Meizu Data Migration blacklist`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.datamigration", PUBLIC_DATA, "com.meizu.datamigration/files/blacklist")
        pos("com.meizu.datamigration", PUBLIC_DATA, "com.meizu.datamigration/files/blacklist/BLACK_LIST_CACHE")
        confirm(create())
    }

    @Test fun `Meizu Music cover cache`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.media.music", PUBLIC_DATA, "com.meizu.media.music/notify_cover")
        pos("com.meizu.media.music", PUBLIC_DATA, "com.meizu.media.music/notify_cover/something")
        confirm(create())
    }

    @Test fun `Photo Resizer temp files`() = runTest {
        addDefaultNegatives()
        neg("com.zmobileapps.photoresizer", SDCARD, "temp.jpg")
        neg("com.zmobileapps.photoresizer", SDCARD, ".test.jpg")
        pos("com.zmobileapps.photoresizer", SDCARD, ".temp.jpg")
        confirm(create())
    }

    @Test fun `UC Mobile notify cover`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.media.music", PUBLIC_DATA, "com.meizu.media.music/notify_cover")
        pos("com.meizu.media.music", PUBLIC_DATA, "com.meizu.media.music/notify_cover/something")
        confirm(create())
    }

    @Test fun `Instashot log and temp files`() = runTest {
        addDefaultNegatives()
        neg("com.camerasideas.instashot", PUBLIC_DATA, "com.camerasideas.instashot/files/inshot/.log")
        pos("com.camerasideas.instashot", PUBLIC_DATA, "com.camerasideas.instashot/files/inshot/.log/123")
        pos("com.camerasideas.instashot", PUBLIC_DATA, "com.camerasideas.instashot/files/.temp.jpg")
        confirm(create())
    }

    @Test fun `Fight Battle temp files`() = runTest {
        addDefaultNegatives()
        neg("best.photo.app.fightbattle", SDCARD, "FightBattle")
        pos("best.photo.app.fightbattle", SDCARD, "FightBattle/.temp.jpg")
        pos("best.photo.app.fightbattle", SDCARD, "FightBattle/.temp1.jpg")
        pos("best.photo.app.fightbattle", SDCARD, "FightBattle/.temp2.jpg")
        pos("best.photo.app.fightbattle", SDCARD, "FightBattle/.smalltemp.jpg")
        pos("best.photo.app.fightbattle", SDCARD, "FightBattle/.smalltemp1.jpg")
        confirm(create())
    }

    @Test fun `MIUI Gallery disk cache`() = runTest {
        addDefaultNegatives()
        neg("com.miui.gallery", PUBLIC_DATA, "com.miui.gallery/files/gallery_disk")
        pos(
            "com.miui.gallery",
            PUBLIC_DATA,
            "com.miui.gallery/files/gallery_disk_cache/small_size/a800e51a74e4a3383ed8bf47f2d5a33e016c0dbbbf8043bf7b422274f79ced5a.0"
        )
        pos(
            "com.miui.gallery",
            PUBLIC_DATA,
            "com.miui.gallery/files/gallery_disk_cache/a800e51a74e4a3383ed8bf47f2d5a33e016c0dbbbf8043bf7b422274f79ced5a.0"
        )
        confirm(create())
    }

    @Test fun `Tinny GIF Maker disk cache`() = runTest {
        addDefaultNegatives()
        neg("videoeditor.videomaker.videoeditorforyoutube", PUBLIC_DATA, "Video.Guru/.disk_cache")
        pos("videoeditor.videomaker.videoeditorforyoutube", PUBLIC_DATA, "Video.Guru/.disk_cache/something")
        confirm(create())
    }

    @Test fun `Visky Gallery secure cache`() = runTest {
        addDefaultNegatives()
        neg("com.visky.gallery", SDCARD, ".Android/.data/com.visky.gallery.data/.data/.secure/.cache")
        pos("com.visky.gallery", SDCARD, ".Android/.data/com.visky.gallery.data/.data/.secure/.cache/file")
        confirm(create())
    }

    @Test fun `MIUI Gallery temp and cache`() = runTest {
        addDefaultNegatives()
        neg("com.miui.gallery", SDCARD, "DCIM/Creative/temp")
        pos("com.miui.gallery", SDCARD, "DCIM/Creative/temp/file")
        neg("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.cache")
        pos("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.cache/$rngString")
        confirm(create())
    }

    @Test fun `Huawei Theme Manager cache`() = runTest {
        addDefaultNegatives()
        neg("com.huawei.android.thememanager", SDCARD, "Huawei/Themes/something")
        pos("com.huawei.android.thememanager", SDCARD, "Huawei/Themes/.cache/6ba49cfe32916e890491ee101f97424d.thumb")
        pos("com.huawei.android.thememanager", SDCARD, "Huawei/Themes/.cache/Explorer.hwt/preview/icon_small.jpg")
        pos("com.huawei.android.thememanager", SDCARD, "Huawei/Themes/.cache/Explorer.hwt")
        confirm(create())
    }

    @Test fun `FaceMoji HTTP cache`() = runTest {
        addDefaultNegatives()
        neg("com.facemoji.lite.xiaomi", PUBLIC_DATA, "com.facemoji.lite.xiaomi/files/okhttp_cache")
        pos("com.facemoji.lite.xiaomi", PUBLIC_DATA, "com.facemoji.lite.xiaomi/files/okhttp_cache1798737084")
        pos("com.facemoji.lite.xiaomi", PUBLIC_DATA, "com.facemoji.lite.xiaomi/files/okhttp_cacheany")
        confirm(create())
    }


    @Test fun `vimages cache`() = runTest {
        addDefaultNegatives()
        neg("com.vimage.android", SDCARD, "Movies/movie.mp4")
        neg("com.vimage.android", SDCARD, "Movies/Vimages/selfie.jpg")
        neg("com.vimage.android", SDCARD, "Movies/Vimages/.data")
        pos("com.vimage.android", SDCARD, "Movies/Vimages/.data/$rngString")

        confirm(create())
    }

    @Test fun `xiaomi fast connect cache`() = runTest {
        addDefaultNegatives()
        neg("com.xiaomi.bluetooth", SDCARD, "Download/MiuiFastConnect")
        pos("com.xiaomi.bluetooth", SDCARD, "Download/MiuiFastConnect/$rngString")

        confirm(create())
    }

    @Test fun `movie fx`() = runTest {
        neg("tv.waterston.movieridefx", SDCARD, "MovieRideFXtmp")
        pos("tv.waterston.movieridefx", SDCARD, "MovieRideFXtmp.mp4")
        confirm(create())
    }

    @Test fun `winrar temp files`() = runTest {
        neg("com.rarlab.rar", PUBLIC_DATA, "com.rarlab.rar/files")
        pos("com.rarlab.rar", PUBLIC_DATA, "com.rarlab.rar/files/_rartemp_open_1")
        pos("com.rarlab.rar", PUBLIC_DATA, "com.rarlab.rar/files/_rartemp_open_2")
        pos("com.rarlab.rar", PUBLIC_DATA, "com.rarlab.rar/files/_rartemp_closed_2")
        pos("com.rarlab.rar", PUBLIC_DATA, "com.rarlab.rar/files/_rartemp_open_123456789")
        confirm(create())
    }

    @Test fun `quicksearchbox blobs`() = runTest {
        neg(
            "com.google.android.googlequicksearchbox",
            PUBLIC_DATA,
            "com.google.android.googlequicksearchbox/files/pending_blobs"
        )
        pos(
            "com.google.android.googlequicksearchbox",
            PUBLIC_DATA,
            "com.google.android.googlequicksearchbox/files/pending_blobs/something"
        )
        confirm(create())
    }

    @Test fun `chinese shopping framework`() = runTest {
        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/.gs_fs0")
        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/AVFSCache")
        pos("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/AVFSCache/laz_homepage_module")
        pos("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/AVFSCache/some/random/file")
        confirm(create())
    }

    @Test fun `agricultural bank of china`() = runTest {
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/image")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/imageX")
        pos("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/image/file")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/nebulaDownload/downloads")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/nebulaDownload/downloadsX")
        pos("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/nebulaDownload/downloads/file")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/image")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/imageX")
        neg("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/imageX/file")
        pos("com.android.bankabc", PUBLIC_DATA, "com.android.bankabc/files/image/file")
        confirm(create())
    }

    @Test fun `alipay caches`() = runTest {
        neg("com.eg.android.AlipayGphone", PRIVATE_DATA, "com.eg.android.AlipayGphone/app_alipay_msp_disk_cache")
        pos(
            "com.eg.android.AlipayGphone",
            PRIVATE_DATA,
            "com.eg.android.AlipayGphone/app_alipay_msp_disk_cache/deleteme"
        )
        neg("com.eg.android.AlipayGphone", PRIVATE_DATA, "com.eg.android.AlipayGphone/files/cashier_templates")
        pos("com.eg.android.AlipayGphone", PRIVATE_DATA, "com.eg.android.AlipayGphone/files/cashier_templates/deleteme")
        neg("com.eg.android.AlipayGphone", PRIVATE_DATA, "com.eg.android.AlipayGphone/files/ccdn/caches/packages")
        pos(
            "com.eg.android.AlipayGphone",
            PRIVATE_DATA,
            "com.eg.android.AlipayGphone/files/ccdn/caches/packages/deleteme"
        )
        neg("com.eg.android.AlipayGphone", PRIVATE_DATA, "com.eg.android.AlipayGphone/files/ccdn/caches/resources")
        pos(
            "com.eg.android.AlipayGphone",
            PRIVATE_DATA,
            "com.eg.android.AlipayGphone/files/ccdn/caches/resources/deleteme"
        )

        neg(
            "com.eg.android.AlipayGphone",
            PUBLIC_DATA,
            "com.eg.android.AlipayGphone/files/multimedia/1a2b3c4d5e6f7g8h9i0jklmnopqrstuvwx/ef/"
        )
        pos(
            "com.eg.android.AlipayGphone",
            PUBLIC_DATA,
            "com.eg.android.AlipayGphone/files/multimedia/1a2b3c4d5e6f7g8h9i0jklmnopqrstuvwx/ef/deleteme"
        )

        neg("com.eg.android.AlipayGphone", SDCARD, "alipay/com.eg.android.AlipayGphone/nebulaDownload/downloads/")
        pos(
            "com.eg.android.AlipayGphone",
            SDCARD,
            "alipay/com.eg.android.AlipayGphone/nebulaDownload/downloads/deleteme"
        )

        neg("com.eg.android.AlipayGphone", SDCARD, "alipay/com.eg.android.AlipayGphone/openplatform/downloads/")
        pos("com.eg.android.AlipayGphone", SDCARD, "alipay/com.eg.android.AlipayGphone/openplatform/downloads/deleteme")

        neg("com.eg.android.AlipayGphone", SDCARD, "alipay/multimedia/1a2b3c4d5e6f7g8h9i0jklmnopqrstuvwx/1f/")
        pos("com.eg.android.AlipayGphone", SDCARD, "alipay/multimedia/1a2b3c4d5e6f7g8h9i0jklmnopqrstuvwx/1f/deleteme")

        confirm(create())
    }

    @Test fun `jing dong caches`() = runTest {
        neg("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/custom_theme_pics")
        pos("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/custom_theme_pics/deleteme")
        neg("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/jingdongbannerBgVideo")
        pos("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/jingdongbannerBgVideo/deleteme")
        neg("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/jingdonghomeSkuVideo")
        pos("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/jingdonghomeSkuVideo/deleteme")
        neg("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/start_image")
        pos("com.jingdong.app.mall", PRIVATE_DATA, "com.jingdong.app.mall/files/start_image/deleteme")

        neg("com.jingdong.app.mall", PUBLIC_DATA, "com.jingdong.app.mall/files/image")
        pos("com.jingdong.app.mall", PUBLIC_DATA, "com.jingdong.app.mall/files/image/deleteme")

        neg("com.jingdong.app.mall", SDCARD, "JDIM/cache")
        pos("com.jingdong.app.mall", SDCARD, "JDIM/cache/image/deleteme")
        neg("com.jingdong.app.mall", SDCARD, "JDIM/cache/image/.nomedia")

        confirm(create())
    }

    @Test fun `netease cloudmusic hidden caches`() = runTest {
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/NetworkCache")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/NetworkCache/deleteme")
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/sailfish")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/sailfish/deleteme")

        confirm(create())
    }

    @Test fun `seazon feedme feedly`() = runTest {
        neg("com.seazon.feedme", PUBLIC_DATA, "com.seazon.feedme/files/providers/Feedly/cache")
        pos("com.seazon.feedme", PUBLIC_DATA, "com.seazon.feedme/files/providers/Feedly/cache/deleteme")
        neg("com.seazon.feedme", PUBLIC_DATA, "com.seazon.feedme/files/providers/Feedly/states")
        pos("com.seazon.feedme", PUBLIC_DATA, "com.seazon.feedme/files/providers/Feedly/states/deleteme")

        confirm(create())
    }

    @Test fun `suing dot com`() = runTest {
        neg("com.suning.mobile.ebuy", PUBLIC_DATA, "com.suning.mobile.ebuy/files/Pictures/cache")
        pos("com.suning.mobile.ebuy", PUBLIC_DATA, "com.suning.mobile.ebuy/files/Pictures/cache/deleteme")

        neg("com.suning.mobile.ebuy", SDCARD, "oneplayer/.local")
        pos("com.suning.mobile.ebuy", SDCARD, "oneplayer/.local/deleteme")

        confirm(create())
    }

    @Test fun `Xianyu cache`() = runTest {
        neg("com.taobao.idlefish", PUBLIC_DATA, "com.taobao.idlefish/files/AVFSCache")
        pos("com.taobao.idlefish", PUBLIC_DATA, "com.taobao.idlefish/files/AVFSCache/deleteme")

        confirm(create())
    }

    @Test fun `Taobao cache`() = runTest {
        neg("com.taobao.taobao", PRIVATE_DATA, "com.taobao.taobao/app_zcache")
        pos("com.taobao.taobao", PRIVATE_DATA, "com.taobao.taobao/app_zcache/deleteme")

        neg("com.taobao.taobao", PRIVATE_DATA, "com.taobao.taobao/files/AVFSCache")
        pos("com.taobao.taobao", PRIVATE_DATA, "com.taobao.taobao/files/AVFSCache/deleteme")

        neg("com.taobao.taobao", PUBLIC_DATA, "com.taobao.taobao/files/AVFSCache")
        pos("com.taobao.taobao", PUBLIC_DATA, "com.taobao.taobao/files/AVFSCache/deleteme")

        confirm(create())
    }

    @Test fun `qq downloader`() = runTest {
        neg("com.tencent.android.qqdownloader", PUBLIC_DATA, "com.tencent.android.qqdownloader/files/tassistant/apk")
        pos(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/apk/deleteme"
        )

        neg("com.tencent.android.qqdownloader", PUBLIC_DATA, "com.tencent.android.qqdownloader/files/tassistant/gif")
        pos(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/gif/deleteme"
        )

        neg(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/mediaCache"
        )
        pos(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/mediaCache/deleteme"
        )

        neg("com.tencent.android.qqdownloader", PUBLIC_DATA, "com.tencent.android.qqdownloader/files/tassistant/pic")
        pos(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/pic/deleteme"
        )

        neg("com.tencent.android.qqdownloader", PUBLIC_DATA, "com.tencent.android.qqdownloader/files/tassistant/video")
        pos(
            "com.tencent.android.qqdownloader",
            PUBLIC_DATA,
            "com.tencent.android.qqdownloader/files/tassistant/video/deleteme"
        )

        confirm(create())
    }

    @Test fun `qq chat`() = runTest {
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/.info")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/.info/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/ae/camera/capture")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/ae/camera/capture/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/opensdk_tmp")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/opensdk_tmp/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/QWallet/.tmp")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/QWallet/.tmp/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/tbs/TbsReaderTemp")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/tbs/TbsReaderTemp/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/tencent/MobileQQ/.pendant")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/tencent/MobileQQ/.pendant/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/uploader")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/files/uploader/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/qzone")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/qzone/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/mini/files")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/mini/files/deletem")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/.emotionsm")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/.emotionsm/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/.gift")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/.gift/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/pddata/app/offline")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/pddata/app/offline/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/photo")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/photo/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/Scribble/ScribbleCache")
        pos(
            "com.tencent.mobileqq",
            PUBLIC_DATA,
            "com.tencent.mobileqq/Tencent/MobileQQ/Scribble/ScribbleCache/deleteme"
        )
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/shortvideo")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/shortvideo/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/tencent/MobileQQ/webso/offline")
        pos(
            "com.tencent.mobileqq",
            PUBLIC_DATA,
            "com.tencent.mobileqq/Tencent/MobileQQ/tencent/MobileQQ/webso/offline/deleteme"
        )
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/thumb")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/MobileQQ/thumb/deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/QQfile_recv/")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/QQfile_recv/.deleteme")
        neg("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/Qzone/.AppCenterImgCache")
        pos("com.tencent.mobileqq", PUBLIC_DATA, "com.tencent.mobileqq/Tencent/Qzone/.AppCenterImgCache/deleteme")
        neg(
            "com.tencent.mobileqq",
            PUBLIC_DATA,
            "com.tencent.mobileqq/Tencent/TMAssistantSDK/Download/com.tencent.mobileqq"
        )
        pos(
            "com.tencent.mobileqq",
            PUBLIC_DATA,
            "com.tencent.mobileqq/Tencent/TMAssistantSDK/Download/com.tencent.mobileqq/deleteme"
        )

        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp/Cache")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp/Cache/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp_qzone/Cache")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp_qzone/Cache/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp_tool/Cache")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ArkApp_tool/Cache/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/far")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/far/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/fdmon")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/fdmon/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/gvideo")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/gvideo/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/pddata/app/offline")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/pddata/app/offline/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/ShadowPlugin_ilive-pluginapngCache")
        pos(
            "com.tencent.mobileqq",
            PRIVATE_DATA,
            "com.tencent.mobileqq/files/ShadowPlugin_ilive-pluginapngCache/deleteme"
        )
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/tempFil")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/tempFile")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/WebOfflineRes")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/files/WebOfflineRes/deleteme")

        confirm(create())
    }

    @Test fun `SHAREit caches`() = runTest {
        val pkgs = setOf(
            "com.lenovo.anyshare.gps",
            "com.lenovo.anyshare",
            "shareit.lite",
            "shareit.premium",
        )
        pkgs.forEach {
            neg(it, PUBLIC_DATA, "$it/files/SHAREit/.caches")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit/.caches/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.caches")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.caches/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.caches")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.caches/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit/.status")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit/.status/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.status")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Lite/.status/anything")
            neg(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.status")
            pos(it, PUBLIC_DATA, "$it/files/SHAREit Premium/.status/anything")
            neg(it, SDCARD, "SHAREit/.caches")
            pos(it, SDCARD, "SHAREit/.caches/anything")
            neg(it, SDCARD, "SHAREit Lite/.caches")
            pos(it, SDCARD, "SHAREit Lite/.caches/anything")
            neg(it, SDCARD, "SHAREit Premium/.caches")
            pos(it, SDCARD, "SHAREit Premium/.caches/anything")
            neg(it, SDCARD, "SHAREit/.status")
            pos(it, SDCARD, "SHAREit/.status/anything")
            neg(it, SDCARD, "SHAREit Lite/.status")
            pos(it, SDCARD, "SHAREit Lite/.status/anything")
            neg(it, SDCARD, "SHAREit Premium/.status")
            pos(it, SDCARD, "SHAREit Premium/.status/anything")
        }
        confirm(create())
    }


    @Test fun `samsung cloud tmp`() = runTest {
        neg("com.samsung.android.scloud", SDCARD, "scloud/tmp")
        pos("com.samsung.android.scloud", SDCARD, "sdcloud/tmp/deleteme")

        confirm(create())
    }
}