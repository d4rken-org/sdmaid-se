package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.addCandidate
import eu.darken.sdmse.appcleaner.core.forensics.locs
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pkgs
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.appcleaner.core.forensics.prefixFree
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

    @Test fun testHiddenCacheDefaults() = runTest {
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

    @Test fun testFilterSdmTest() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("eu.thedarken.sdm.test").locs(PUBLIC_DATA).prefixFree("com.soundcloud.android/files/skippy")
        )
        addCandidate(pos().pkgs("eu.thedarken.sdm.test").locs(SDCARD).prefixFree("sdm_test_file_cache_v2"))
        addCandidate(pos().pkgs("eu.thedarken.sdm.test").locs(SDCARD).prefixFree("sdm_test_file_cache1"))
        addCandidate(pos().pkgs("eu.thedarken.sdm.test").locs(PUBLIC_DATA).prefixFree("sdm_test_file_cache2"))
        addCandidate(pos().pkgs("eu.thedarken.sdm.test").locs(PRIVATE_DATA).prefixFree("sdm_test_file_cache3"))
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test").locs(PUBLIC_DATA)
                .prefixFree("eu.thedarken.sdm.test/sdm_test_internal_hidden_cache_direct")
        )
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test").locs(PUBLIC_DATA)
                .prefixFree("eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/$rngString")
        )
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test").locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/$rngString")
        )
        addCandidate(
            pos().pkgs("eu.thedarken.sdm.test").locs(PRIVATE_DATA).prefixFree(
                "eu.thedarken.sdm.test/sdm_test_internal_hidden_cache/" + UUID.randomUUID()
                    .toString() + "/" + rngString
            )
        )
        confirm(create())
    }

    @Test fun testFilterSoundcloud() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.soundcloud.android").locs(PUBLIC_DATA).prefixFree("com.soundcloud.android/files/skippy")
        )
        addCandidate(
            pos().pkgs("com.soundcloud.android").locs(PUBLIC_DATA)
                .prefixFree("com.soundcloud.android/files/skippy/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAptoide() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("cm.aptoide.pt").locs(SDCARD).prefixFree(".aptoide/icons"))
        addCandidate(
            pos().pkgs("cm.aptoide.pt").locs(SDCARD).prefixFree(".aptoide/icons/$rngString")
        )
        addCandidate(neg().pkgs("cm.aptoide.pt").locs(SDCARD).prefixFree(".aptoide/apks"))
        addCandidate(pos().pkgs("cm.aptoide.pt").locs(SDCARD).prefixFree(".aptoide/apks/com.pandora.android.5.2.apk"))
        addCandidate(pos().pkgs("en.aptoide.com").locs(SDCARD).prefixFree(".aptoide/apks/com.pandora.android.5.2.apk"))
        confirm(create())
    }

    @Test fun testFilterGoogleCamera() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/TEMP_SESSIONS")
        )
        addCandidate(
            pos().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/TEMP_SESSIONS/PANO_455796234_5545668855")
        )
        addCandidate(
            neg().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/refocus")
        )
        addCandidate(
            pos().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/refocus/IMG_12547896_698745214")
        )
        addCandidate(
            neg().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/panorama_sessions")
        )
        addCandidate(
            pos().pkgs("com.google.android.GoogleCamera").locs(PUBLIC_DATA)
                .prefixFree("com.google.android.GoogleCamera/files/panorama_sessions/session_56987415_43214845")
        )
        confirm(create())
    }

    @Test fun testFilterFireFox() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/Cache")
        )
        addCandidate(
            pos().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA).prefixFree(
                "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/Cache/$rngString"
            )
        )
        addCandidate(
            neg().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox/files/mozilla/sqqj1c1o.default/Cache")
        )
        addCandidate(
            pos().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox/files/mozilla/sqqj1c1o.default/Cache/$rngString")
        )
        addCandidate(
            neg().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox/app_tmpdir/")
        )
        addCandidate(
            pos().pkgs("org.mozilla.firefox", "org.mozilla.firefox_beta").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox/app_tmpdir/$rngString")
        )
        confirm(create())
    }

    @Test fun testMozillaFenix() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("org.mozilla.fenix").locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/app_tmpdir/"))
        addCandidate(
            pos().pkgs("org.mozilla.fenix").locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.fenix/app_tmpdir/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterGenieWidget() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.google.android.apps.genie.geniewidget").locs(PRIVATE_DATA)
                .prefixFree("com.google.android.apps.genie.geniewidget/app_news_image_cache")
        )
        addCandidate(
            pos().pkgs("com.google.android.apps.genie.geniewidget").locs(PRIVATE_DATA)
                .prefixFree("com.google.android.apps.genie.geniewidget/app_news_image_cache/abcdefg123456")
        )
        confirm(create())
    }

    @Test fun testFilterBeautifulWidgets() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.levelup.beautifulwidgets", "com.levelup.beautifulwidgets.free").locs(PUBLIC_DATA)
                .prefixFree("com.levelup.beautifulwidgets/files/Pictures")
        )
        addCandidate(
            pos().pkgs("com.levelup.beautifulwidgets", "com.levelup.beautifulwidgets.free").locs(PUBLIC_DATA)
                .prefixFree("com.levelup.beautifulwidgets/files/Pictures/91239123acbbea_540_rounded_top.png")
        )
        confirm(create())
    }

    @Test fun testFilterFrostWire() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.frostwire.android").locs(SDCARD).prefixFree("FrostWire/.image_cache"))
        addCandidate(
            pos().pkgs("com.frostwire.android").locs(SDCARD)
                .prefixFree("FrostWire/.image_cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterWallbase() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.citc.wallbase").locs(SDCARD).prefixFree("Wallbase/.WallbaseCache"))
        addCandidate(
            pos().pkgs("com.citc.wallbase").locs(SDCARD)
                .prefixFree("Wallbase/.WallbaseCache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterFacebookKatana() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.facebook.katana").locs(SDCARD).prefixFree("com.facebook.katana/fb_temp"))
        addCandidate(
            pos().pkgs("com.facebook.katana").locs(SDCARD)
                .prefixFree("com.facebook.katana/fb_temp/.facebook_455866544.jpg")
        )
        addCandidate(
            neg().pkgs("com.facebook.katana").locs(PRIVATE_DATA).prefixFree("com.facebook.katana/files/video-cache")
        )
        addCandidate(
            pos().pkgs("com.facebook.katana").locs(PRIVATE_DATA)
                .prefixFree("com.facebook.katana/files/video-cache/$rngString")
        )
        addCandidate(
            neg().pkgs("com.facebook.katana").locs(PRIVATE_DATA)
                .prefixFree("com.facebook.katana/files/ExoPlayerCacheDir")
        )
        addCandidate(
            pos().pkgs("com.facebook.katana").locs(PRIVATE_DATA)
                .prefixFree("com.facebook.katana/files/ExoPlayerCacheDir/$rngString")
        )
        addCandidate(neg().pkgs("com.facebook.katana").locs(SDCARD).prefixFree(".facebook_cache"))
        addCandidate(
            pos().pkgs("com.facebook.katana").locs(SDCARD).prefixFree(".facebook_cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterFacebookOcra() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.facebook.orca").locs(SDCARD).prefixFree("com.facebook.orca/fb_temp"))
        addCandidate(
            pos().pkgs("com.facebook.orca").locs(SDCARD).prefixFree("com.facebook.orca/fb_temp/.facebook_455866544.jpg")
        )
        addCandidate(
            neg().pkgs("com.facebook.orca").locs(PRIVATE_DATA).prefixFree("com.facebook.orca/files/ExoPlayerCacheDir")
        )
        addCandidate(
            pos().pkgs("com.facebook.orca").locs(PRIVATE_DATA)
                .prefixFree("com.facebook.orca/files/ExoPlayerCacheDir/$rngString")
        )
        addCandidate(neg().pkgs("com.facebook.orca").locs(SDCARD).prefixFree(".facebook_cache"))
        addCandidate(
            pos().pkgs("com.facebook.orca").locs(SDCARD).prefixFree(".facebook_cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAntutuGL2() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.antutu.ABenchMark.GL2").locs(PUBLIC_DATA)
                .prefixFree("com.antutu.ABenchMark.GL2/files/shader")
        )
        addCandidate(
            pos().pkgs("com.antutu.ABenchMark.GL2").locs(PUBLIC_DATA)
                .prefixFree("com.antutu.ABenchMark.GL2/files/shader/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterDoodleJump() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.lima.doodlejump").locs(PUBLIC_DATA).prefixFree("com.lima.doodlejump/files/cache"))
        addCandidate(
            pos().pkgs("com.lima.doodlejump").locs(PUBLIC_DATA)
                .prefixFree("com.lima.doodlejump/files/cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAntutu() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.antutu.ABenchMark", "com.qqfriends.com.music").locs(SDCARD)
                .prefixFree(".antutu/benchmark/avatars")
        )
        addCandidate(
            pos().pkgs("com.antutu.ABenchMark", "com.qqfriends.com.music").locs(SDCARD)
                .prefixFree(".antutu/benchmark/avatars/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAudials() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.audials", "com.audials.paid").locs(SDCARD).prefixFree("Audials/temporary/"))
        addCandidate(
            pos().pkgs("com.audials", "com.audials.paid").locs(SDCARD)
                .prefixFree("Audials/temporary/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAlicoid() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.magine.aliceoid").locs(PRIVATE_DATA).prefixFree("com.magine.aliceoid/data/images"))
        addCandidate(
            pos().pkgs("com.magine.aliceoid").locs(PRIVATE_DATA)
                .prefixFree("com.magine.aliceoid/data/images/12345-67890")
        )
        confirm(create())
    }

    @Test fun testFilterFlightRadar() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.flightradar24pro").locs(SDCARD).prefixFree("FlightRadar24/Alerts"))
        addCandidate(pos().pkgs("com.flightradar24pro").locs(SDCARD).prefixFree("FlightRadar24/Alerts/1234567890"))
        confirm(create())
    }

    @Test fun testFilterPolarisOffice() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf(
            "com.infraware.polarisoffice",
            "com.infraware.polarisoffice5.entbiz.symantec",
            "com.infraware.polarisoffice.entbiz.citrix",
            "com.infraware.polarisoffice.entbiz.gd.viewer",
            "com.infraware.office.link",
            "com.infraware.polarisoffice4",
            "com.infraware.polarisoffice.entbiz.gd",
            "com.infraware.polarisoffice5tablet"
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".polaris_temp"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".polaris_temp/$rngString"))
        confirm(create())
    }

    @Test fun testFilterTurboClient() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("turbo.client.free", "turbo.client").locs(SDCARD).prefixFree("Turbo Client/temp"))
        addCandidate(
            pos().pkgs("turbo.client.free", "turbo.client").locs(SDCARD)
                .prefixFree("Turbo Client/temp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterYahooMail() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.yahoo.mobile.client.android.mail").locs(SDCARD).prefixFree("yahoo/mail/imgCache"))
        addCandidate(
            pos().pkgs("com.yahoo.mobile.client.android.mail").locs(SDCARD)
                .prefixFree("yahoo/mail/imgCache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterLGEClipTray() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.lge.software.cliptray").locs(PUBLIC_DATA).prefixFree(".cliptray"))
        addCandidate(neg().pkgs("com.lge.software.cliptray").locs(PUBLIC_DATA).prefixFree(".cliptray/.nomedia"))
        addCandidate(
            pos().pkgs("com.lge.software.cliptray").locs(PUBLIC_DATA)
                .prefixFree(".cliptray/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterEFile() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.domobile.efile").locs(SDCARD).prefixFree(".eFile_trash"))
        addCandidate(
            pos().pkgs("com.domobile.efile").locs(SDCARD).prefixFree(".eFile_trash/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterTencentMovieTicket() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.tencent.movieticket").locs(SDCARD).prefixFree(".QQMovieTicket/.cache"))
        addCandidate(
            pos().pkgs("com.tencent.movieticket").locs(SDCARD)
                .prefixFree(".QQMovieTicket/.cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterSohuVideo() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.sohu.sohuvideo").locs(PUBLIC_DATA).prefixFree("com.sohu.sohuvideo/tempVideo"))
        addCandidate(
            pos().pkgs("com.sohu.sohuvideo").locs(PUBLIC_DATA)
                .prefixFree("com.sohu.sohuvideo/tempVideo/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterTencentLbsCheckin() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.tencent.lbs.checkin").locs(SDCARD).prefixFree(".lbsGo/avatar_cache"))
        addCandidate(
            pos().pkgs("com.tencent.lbs.checkin").locs(SDCARD)
                .prefixFree(".lbsGo/avatar_cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterWuba() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.wuba.jiaoyou").locs(SDCARD).prefixFree("wuba/jiaoyou/casheimage"))
        addCandidate(
            pos().pkgs("com.wuba.jiaoyou").locs(SDCARD)
                .prefixFree("wuba/jiaoyou/casheimage/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterEverNoteSkitch() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.evernote.skitch").locs(SDCARD).prefixFree("Skitch/Temp"))
        addCandidate(
            pos().pkgs("com.evernote.skitch").locs(SDCARD).prefixFree("Skitch/Temp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAutoNavi() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.telenav.doudouyou.android.autonavi").locs(PUBLIC_DATA)
                .prefixFree("com.telenav.doudouyou.android.autonavi/cacheimage")
        )
        addCandidate(
            pos().pkgs("com.telenav.doudouyou.android.autonavi").locs(PUBLIC_DATA)
                .prefixFree("com.telenav.doudouyou.android.autonavi/cacheimage/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterYahooWeather() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.yahoo.mobile.client.android.weather").locs(SDCARD).prefixFree("yahoo/weather/imgCache")
        )
        addCandidate(
            pos().pkgs("com.yahoo.mobile.client.android.weather").locs(SDCARD)
                .prefixFree("yahoo/weather/imgCache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterBahnMultiCity() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("de.bahn.multicity").locs(SDCARD).prefixFree("cache"))
        addCandidate(pos().pkgs("de.bahn.multicity").locs(SDCARD).prefixFree("cache/mc.json"))
        confirm(create())
    }

    @Test fun testFilterPerfectScreenshot() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot").locs(SDCARD)
                .prefixFree("Pictures/perfectscreenshot_tmp")
        )
        addCandidate(
            neg().pkgs("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot").locs(SDCARD)
                .prefixFree("Pictures/perfectscreenshot_tmp/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.mikedepaul.perfectscreenshotadfree", "com.mikedepaul.perfectscreenshot").locs(SDCARD)
                .prefixFree("Pictures/perfectscreenshot_tmp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterCleanMasterGuard() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA).prefixFree("com.cleanmaster.mguard/files/cache")
        )
        addCandidate(
            pos().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/cache/$rngString")
        )
        addCandidate(
            neg().pkgs("com.cleanmaster.mguard").locs(PRIVATE_DATA).prefixFree("com.cleanmaster.mguard/files/cache")
        )
        addCandidate(
            pos().pkgs("com.cleanmaster.mguard").locs(PRIVATE_DATA)
                .prefixFree("com.cleanmaster.mguard/files/cache/$rngString")
        )
        addCandidate(
            neg().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA).prefixFree("com.cleanmaster.mguard/files/iconcache")
        )
        addCandidate(
            pos().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/iconcache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterMagix() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.magix.camera_mx").locs(SDCARD).prefixFree("Magix/.tmp"))
        addCandidate(
            pos().pkgs("com.magix.camera_mx").locs(SDCARD).prefixFree("Magix/.tmp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterFlipboard() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("flipboard.app").locs(PUBLIC_DATA).prefixFree("flipboard.app/files/cache"))
        addCandidate(
            pos().pkgs("flipboard.app").locs(PUBLIC_DATA)
                .prefixFree("flipboard.app/files/cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterNineGag() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/images")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/images/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/images/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/gags")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gags/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gags/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gags_thumb")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gags_thumb/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gags_thumb/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/gifs")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gifs/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/gifs/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/mp4s")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/mp4s/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/mp4s/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/uploads")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/uploads/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/uploads/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/avatar")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/avatar/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/avatar/$rngString")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA).prefixFree("com.ninegag.android.app/files/covers")
        )
        addCandidate(
            neg().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/covers/.nomedia")
        )
        addCandidate(
            pos().pkgs("com.ninegag.android.app").locs(PUBLIC_DATA)
                .prefixFree("com.ninegag.android.app/files/covers/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterAmazonMusic() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.amazon.mp3", "com.amazon.bueller.music").locs(SDCARD).prefixFree("amazonmp3/temp"))
        addCandidate(
            pos().pkgs("com.amazon.mp3", "com.amazon.bueller.music").locs(SDCARD)
                .prefixFree("amazonmp3/temp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterSimplePlanes() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.jundroo.SimplePlanes").locs(SDCARD).prefixFree(".EveryplayCache/com.jundroo.SimplePlanes")
        )
        addCandidate(
            pos().pkgs("com.jundroo.SimplePlanes").locs(SDCARD)
                .prefixFree(".EveryplayCache/com.jundroo.SimplePlanes/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterPolarisViewer() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.infraware.polarisviewer4").locs(SDCARD).prefixFree(".PolarisViewer4/polarisTemp"))
        addCandidate(
            pos().pkgs("com.infraware.polarisviewer4").locs(SDCARD)
                .prefixFree(".PolarisViewer4/polarisTemp/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterSlingPlayer() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf(
            "com.slingmedia.slingPlayer",
            "com.slingmedia.slingPlayerTablet",
            "com.slingmedia.slingPlayerTabletFreeApp",
            "com.slingmedia.slingPlayerFreeApp"
        )
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayer/files/tsDump"))
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayer/files/logDump"))
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerTablet/files/tsDump"))
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerTablet/files/logDump"))
        addCandidate(
            neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerTabletFreeApp/files/tsDump")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerTabletFreeApp/files/logDump")
        )
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerFreeApp/files/tsDump"))
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.slingmedia.slingPlayerFreeApp/files/logDump"))
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayer/files/tsDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayer/files/logDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerTablet/files/tsDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerTablet/files/logDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerTabletFreeApp/files/tsDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerTabletFreeApp/files/logDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerFreeApp/files/tsDump/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.slingmedia.slingPlayerFreeApp/files/logDump/$rngString")
        )
        confirm(create())
    }

    @Test fun testFilterStockGallery() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.sec.android.gallery3d").locs(PRIVATE_DATA).prefixFree("com.sec.android.gallery3d"))
        addCandidate(
            neg().pkgs("com.sec.android.gallery3d").locs(PRIVATE_DATA).prefixFree("com.sec.android.gallery3d/Temp")
        )
        addCandidate(
            pos().pkgs("com.sec.android.gallery3d").locs(PRIVATE_DATA)
                .prefixFree("com.sec.android.gallery3d/Temp/1456180349484")
        )
        confirm(create())
    }

    @Test fun testFilterSkype() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/media_cache")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/media_cache_v2")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache_v2")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/media_cache")
        )
        addCandidate(
            neg().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/media_cache_v2")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/emo_cache_v2/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/media_cache/asyncdb/cache_db.db")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/darkenlaptop/media_messaging/media_cache_v3/asyncdb/cache_db.db")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache_v2/^139B4B1968EDE294E5FBC931E304C7E94673B7D70FCB2A60B0^pdefault_60_5cd18fd4-7235-48b9-871f-0210e1e1e7df_distr.png")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/emo_cache_v2/^5D2361D8C90B1FA5B1312CDED6C0CEFAF9A125AE9BBA62B2B4^pthumbnail_28f516c6-9074-453b-b076-90bc11a39e3a_distr")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/media_cache/asyncdb/cache_db.db")
        )
        addCandidate(
            pos().pkgs("com.skype.raider", "com.skype.rover").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/darkenlaptop/media_messaging/media_cache_v3/asyncdb/cache_db.db")
        )
        confirm(create())
    }

    @Test fun testESFileExplorer() = runTest {
        val pkgs = arrayOf(
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.cupcake",
            "com.estrongs.android.pop.app.shortcut",
            "com.estrongs.android.pop.pro"
        )
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/dianxin/notify/.cache/" + UUID.randomUUID()))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/dianxin/notify/.cache"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("dianxin/notify/.cache/" + UUID.randomUUID()))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("dianxin/notify/.cache"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/recycle/" + UUID.randomUUID()))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/recycle"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".BD_SAPI_CACHE/" + UUID.randomUUID()))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".BD_SAPI_CACHE"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("BD_SAPI_CACHE"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".BD_SAPI"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/.app_icon_back"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/.app_icon_back/strawberrycake"))
        confirm(create())
    }

    @Test fun testCMLauncher() = runTest {
        addCandidate(
            pos().pkgs("com.ksmobile.launcher").locs(PUBLIC_DATA)
                .prefixFree("com.ksmobile.launcher/files/iconcache/" + UUID.randomUUID())
        )
        addCandidate(
            neg().pkgs("com.ksmobile.launcher").locs(PUBLIC_DATA).prefixFree("com.ksmobile.launcher/files/iconcache")
        )
        confirm(create())
    }

    @Test fun testAsusWebstorage() = runTest {
        addCandidate(
            pos().pkgs("com.ecareme.asuswebstorage").locs(PUBLIC_DATA)
                .prefixFree("com.ecareme.asuswebstorage/folderBrowseCache/" + UUID.randomUUID())
        )
        addCandidate(
            neg().pkgs("com.ecareme.asuswebstorage").locs(PUBLIC_DATA)
                .prefixFree("com.ecareme.asuswebstorage/folderBrowseCache")
        )
        confirm(create())
    }

    @Test fun testEveryplayAppCache() = runTest {
        addCandidate(
            pos().pkgs("com.everyplay.everyplayapp").locs(SDCARD)
                .prefixFree(".EveryplayCache/images/" + UUID.randomUUID())
        )
        addCandidate(
            neg().pkgs("com.everyplay.everyplayapp").locs(SDCARD)
                .prefixFree(".EveryplayCache/com.some.package/" + UUID.randomUUID())
        )
        addCandidate(
            neg().pkgs("com.everyplay.everyplayapp").locs(SDCARD)
                .prefixFree(".EveryplayCache/images" + "com.everyplay.everyplayapp")
        )
        confirm(create())
    }

    @Test fun testFaceFolder() = runTest {
        addCandidate(
            pos().pkgs("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d")
                .locs(SDCARD).prefixFree(".face/facedata")
        )
        addCandidate(
            pos().pkgs("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d")
                .locs(SDCARD).prefixFree(".face/11111")
        )
        addCandidate(
            neg().pkgs("com.android.gallery3d", "com.google.android.gallery3d", "com.sec.android.gallery3d")
                .locs(SDCARD).prefixFree(".face")
        )
        confirm(create())
    }

    @Test fun testAmazonMarket_downloadedApks() = runTest {
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/files/update-1-1.apk")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/files/update-12-123456789.apk")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA).prefixFree("com.amazon.mShop.android/files")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/files/some.apk")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/files/update-1-12346789.zip")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA).prefixFree("some.other.app/files/update-1-1.apk")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA)
                .prefixFree("com.amazon.mShop.android/files/apks/update-1-1.apk")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA)
                .prefixFree("com.amazon.mShop.android/files/apks/update-12-123456789.apk")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA).prefixFree("com.amazon.mShop.android/files/apks")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA)
                .prefixFree("com.amazon.mShop.android/files/apks/some.apk")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA)
                .prefixFree("com.amazon.mShop.android/files/apks/update-1-12346789.zip")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PUBLIC_DATA)
                .prefixFree("some.other.app/files/apks/update-1-1.apk")
        )
        confirm(create())
    }

    @Test fun testKate() = runTest {
        val pkgs = arrayOf(
            "com.perm.kate",
            "com.perm.kate.pro",
            "com.perm.kate_new_2"
        )
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".Kate/image_cache/" + UUID.randomUUID()))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".Kate/image_cache/-113291110_-2052025531"))
        addCandidate(neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree(".Kate/image_cache"))
        confirm(create())
    }

    @Test fun testPlanTronicsFindMyHeadset() = runTest {
        addCandidate(pos().pkgs("com.plantronics.findmyheadset").locs(SDCARD).prefixFree("cabotEL.log"))
        addCandidate(neg().pkgs("com.plantronics.findmyheadset").locs(SDCARD).prefixFree("cabotel"))
        confirm(create())
    }

    @Test fun testMotionsStils() = runTest {
        addCandidate(
            pos().pkgs("com.google.android.apps.motionstills").locs(SDCARD)
                .prefixFree("motionstills/warp_grid_cache/test")
        )
        addCandidate(
            neg().pkgs("com.google.android.apps.motionstills").locs(SDCARD)
                .prefixFree("motionstills/warp_grid_cache/.nomedia")
        )
        addCandidate(
            neg().pkgs("com.google.android.apps.motionstills").locs(SDCARD).prefixFree("motionstills/warp_grid_cache")
        )
        addCandidate(neg().pkgs("com.google.android.apps.motionstills").locs(SDCARD).prefixFree("motionstills"))
        confirm(create())
    }

    @Test fun testPlexMediaServer() = runTest {
        addCandidate(
            pos().pkgs("com.plexapp.android").locs(PRIVATE_DATA)
                .prefixFree("com.plexapp.android/Plex Media Server/Cache/test")
        )
        addCandidate(
            neg().pkgs("com.plexapp.android").locs(PRIVATE_DATA)
                .prefixFree("com.plexapp.android/Plex Media Server/Cache")
        )
        confirm(create())
    }

    @Test fun testFotaDownload() = runTest {
        addCandidate(pos().pkgs("com.tcl.ota.bb").locs(SDCARD).prefixFree(".fotadownload/test"))
        addCandidate(neg().pkgs("com.tcl.ota.bb").locs(SDCARD).prefixFree(".fotadownload"))
        confirm(create())
    }

    @Test fun testBrowser() = runTest {
        addCandidate(pos().pkgs("com.android.browser").locs(SDCARD).prefixFree("Browser/MediaCache/Test"))
        addCandidate(neg().pkgs("com.android.browser").locs(SDCARD).prefixFree("Browser/MediaCache"))
        confirm(create())
    }

    @Test fun testUCDownloadsApolloCache() = runTest {
        val pkgs = arrayOf(
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
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("UCDownloads/video/.apolloCache"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("UCDownloads/video/.apolloCache/strawberry"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("UCDownloadsPad/video/.apolloCache"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("UCDownloadsPad/video/.apolloCache/strawberry"))
        addCandidate(
            neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("com.UCMobile.intl/files/UcDownloads/video/.apolloCache")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("com.UCMobile.intl/files/UcDownloads/video/.apolloCache/strawberry")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("supercache"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("supercache/test"))
        confirm(create())
    }

    @Test fun testChromeTempDownloadfile() = runTest {
        addCandidate(neg().pkgs("com.android.chrome").locs(SDCARD).prefixFree("Download"))
        addCandidate(neg().pkgs("com.android.chrome").locs(SDCARD).prefixFree("Download/.crdownload"))
        addCandidate(neg().pkgs("com.android.chrome").locs(SDCARD).prefixFree("$rngString.crdownload"))
        addCandidate(
            pos().pkgs("com.android.chrome").locs(SDCARD).prefixFree("Download/$rngString.crdownload")
        )
        confirm(create())
    }

    @Test fun testMusically() = runTest {
        addCandidate(
            neg().pkgs("com.zhiliaoapp.musically").locs(PUBLIC_DATA)
                .prefixFree("com.zhiliaoapp.musically/files/frescocache")
        )
        addCandidate(
            pos().pkgs("com.zhiliaoapp.musically").locs(PUBLIC_DATA)
                .prefixFree("com.zhiliaoapp.musically/files/frescocache/$rngString")
        )
        addCandidate(
            neg().pkgs("com.zhiliaoapp.musically").locs(PUBLIC_DATA).prefixFree("com.zhiliaoapp.musically/Videos")
        )
        addCandidate(
            pos().pkgs("com.zhiliaoapp.musically").locs(PUBLIC_DATA)
                .prefixFree("com.zhiliaoapp.musically/Videos/$rngString")
        )
        confirm(create())
    }

    @Test fun testSnapchat() = runTest {
        addCandidate(
            neg().pkgs("com.snapchat.android").locs(PRIVATE_DATA).prefixFree("com.snapchat.android/files/media_cache")
        )
        addCandidate(
            pos().pkgs("com.snapchat.android").locs(PRIVATE_DATA)
                .prefixFree("com.snapchat.android/files/media_cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testKeerby() = runTest {
        addCandidate(neg().pkgs("com.keerby.formatfactory").locs(SDCARD).prefixFree("Keerby/FormatFactory/$rngString"))
        addCandidate(
            pos().pkgs("com.keerby.formatfactory").locs(SDCARD).prefixFree("Keerby/FormatFactory/tmp/$rngString")
        )
        confirm(create())
    }

    @Test fun testGooglePlus() = runTest {
        addCandidate(
            neg().pkgs("com.google.android.apps.plus").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.google.android.apps.plus/app_localMedia")
        )
        addCandidate(
            pos().pkgs("com.google.android.apps.plus").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.google.android.apps.plus/app_localMedia/$rngString")
        )
        confirm(create())
    }

    @Test fun testZArchiver() = runTest {
        addCandidate(neg().pkgs("ru.zdevs.zarchiver").locs(PUBLIC_DATA).prefixFree("ru.zdevs.zarchiver/files"))
        addCandidate(
            pos().pkgs("ru.zdevs.zarchiver").locs(PUBLIC_DATA).prefixFree("ru.zdevs.zarchiver/files/$rngString")
        )
        confirm(create())
    }

    @Test fun testNaviKing() = runTest {
        addCandidate(neg().pkgs("com.kingwaytek.naviking").locs(SDCARD).prefixFree("LocalKingMapTempN5"))
        addCandidate(pos().pkgs("com.kingwaytek.naviking").locs(SDCARD).prefixFree("LocalKingMapTempN5/$rngString"))
        confirm(create())
    }

    @Test fun testGlock() = runTest {
        addCandidate(neg().pkgs("com.genie9.glock").locs(SDCARD).prefixFree(".GLock/.cache"))
        addCandidate(pos().pkgs("com.genie9.glock").locs(SDCARD).prefixFree(".GLock/.cache/$rngString"))
        confirm(create())
    }

    @Test fun testTCLUpdater() = runTest {
        addCandidate(neg().pkgs("com.tcl.ota.bb").locs(PUBLIC_DATA).prefixFree(".fotaApps"))
        addCandidate(pos().pkgs("com.tcl.ota.bb").locs(PUBLIC_DATA).prefixFree(".fotaApps/$rngString"))
        confirm(create())
    }

    @Test fun testWhatsAppShared() = runTest {
        addCandidate(neg().pkgs("com.whatsapp").locs(SDCARD).prefixFree("WhatsApp/.Shared"))
        addCandidate(pos().pkgs("com.whatsapp").locs(SDCARD).prefixFree("WhatsApp/.Shared/$rngString"))
        confirm(create())
    }

    /**
     * https://github.com/d4rken/sdmaid-public/issues/2084
     * [GameFilesFilterTest.testUnity3dGameData]
     */
    @Test fun keepUnityOfflineGameData() = runTest {
        addCandidate(
            neg().pkgs("test.pkg").locs(PUBLIC_DATA)
                .prefixFree("test.pkg/files/Cache/t_head_mask_back.tga.unity3d&ux=1538675986")
        )
        addCandidate(
            neg().pkgs("test.pkg").locs(PUBLIC_DATA)
                .prefixFree("test.pkg/files/Cache/$rngString.unity3d&ux=$rngString")
        )
        confirm(create())
    }

    @Test fun testXploreFileManager() = runTest {
        addCandidate(
            neg().pkgs("com.lonelycatgames.Xplore").locs(PUBLIC_DATA)
                .prefixFree("com.lonelycatgames.Xplore/files/Send Anywhere/.temp")
        )
        addCandidate(
            pos().pkgs("com.lonelycatgames.Xplore").locs(PUBLIC_DATA)
                .prefixFree("com.lonelycatgames.Xplore/files/Send Anywhere/.temp/$rngString")
        )
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
        addCandidate(neg().pkgs("com.particlenews.newsbreak").locs(SDCARD).prefixFree(".newsbreak/image"))
        addCandidate(pos().pkgs("com.particlenews.newsbreak").locs(SDCARD).prefixFree(".newsbreak/image/$rngString"))
        confirm(create())
    }

    @Test fun testSqlitePrime() = runTest {
        addCandidate(neg().pkgs("com.lastempirestudio.sqliteprime").locs(SDCARD).prefixFree("SqlitePrime/cache"))
        addCandidate(
            pos().pkgs("com.lastempirestudio.sqliteprime").locs(SDCARD).prefixFree("SqlitePrime/cache/$rngString")
        )
        confirm(create())
    }

    @Test fun testSems() = runTest {
        addCandidate(neg().pkgs("com.samsung.android.mobileservice").locs(SDCARD).prefixFree(".sems"))
        addCandidate(
            pos().pkgs("com.samsung.android.mobileservice").locs(SDCARD)
                .prefixFree(".sems/sa_groups_img_family_grid.png")
        )
        confirm(create())
    }

    @Test fun testQPython() = runTest {
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython"))
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/projects"))
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/.notebook"))
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/lib/1thing"))
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/log"))
        addCandidate(neg().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/cache"))
        addCandidate(pos().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/log/1thing"))
        addCandidate(pos().pkgs("org.qpython.qpy").locs(SDCARD).prefixFree("qpython/cache/1thing"))
        confirm(create())
    }

    @Test fun testVMOS() = runTest {
        addCandidate(neg().pkgs("com.vmos.glb").locs(SDCARD).prefixFree("VMOSfiletransferstation"))
        addCandidate(
            pos().pkgs("com.vmos.glb").locs(SDCARD)
                .prefixFree("VMOSfiletransferstation/something_!\"§$%&/()=?ÄÖ_:;'ÄÖ'")
        )
        confirm(create())
    }

    @Test fun testSketchCode() = runTest {
        addCandidate(neg().pkgs("com.sketch.code.two").locs(SDCARD).prefixFree(".sketchcode"))
        addCandidate(neg().pkgs("com.sketch.code.two").locs(SDCARD).prefixFree(".sketchcode/temp"))
        addCandidate(
            pos().pkgs("com.sketch.code.two").locs(SDCARD)
                .prefixFree(".sketchcode/temp/something_!\"§$%&/()=?ÄÖ_:;'ÄÖ'")
        )
        confirm(create())
    }

    @Test fun testVkontakte() = runTest {
        addCandidate(
            neg().pkgs("com.vkontakte.android", "re.sova.five").locs(SDCARD).prefixFree(".vkontakte/something")
        )
        addCandidate(
            pos().pkgs("com.vkontakte.android", "re.sova.five").locs(SDCARD).prefixFree(".vkontakte/autoplay_gif_cache")
        )
        confirm(create())
    }

    @Test fun testMagicVideoHiddenTmp() = runTest {
        addCandidate(neg().pkgs("com.magicvideo.beauty.videoeditor").locs(SDCARD).prefixFree(".tmp"))
        addCandidate(
            neg().pkgs("com.magicvideo.beauty.videoeditor").locs(SDCARD)
                .prefixFree(".tmp508890d1-5954-404a-a28f-01bbb8d5150")
        )
        addCandidate(
            neg().pkgs("com.magicvideo.beauty.videoeditor").locs(SDCARD)
                .prefixFree(".tmp508890d1-5954-404a-a28f-01bbb8d5150ee")
        )
        addCandidate(
            neg().pkgs("com.magicvideo.beauty.videoeditor").locs(SDCARD)
                .prefixFree(".tmp508890d1-5954-404a-a28f-01bbb8d5150G")
        )
        addCandidate(
            pos().pkgs("com.magicvideo.beauty.videoeditor").locs(SDCARD)
                .prefixFree(".tmp508890d1-5954-404a-a28f-01bbb8d5150e")
        )
        confirm(create())
    }

    @Test fun testLikee() = runTest {
        addCandidate(neg().pkgs("video.like").locs(SDCARD).prefixFree("video.like/nerv-cacheg"))
        addCandidate(pos().pkgs("video.like").locs(SDCARD).prefixFree("video.like/nerv-cache/something"))
        addCandidate(neg().pkgs("video.like").locs(PUBLIC_DATA).prefixFree("video.like/files/kk"))
        addCandidate(pos().pkgs("video.like").locs(PUBLIC_DATA).prefixFree("video.like/files/kk/something"))
        addCandidate(neg().pkgs("video.like").locs(PUBLIC_DATA).prefixFree("video.like/files/xlog"))
        addCandidate(pos().pkgs("video.like").locs(PUBLIC_DATA).prefixFree("video.like/files/xlog/test"))
        confirm(create())
    }

    @Test fun testLuumi() = runTest {
        addCandidate(neg().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD).prefixFree("Lumii/.cache"))
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD).prefixFree("Lumii/.cache/something")
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD).prefixFree("Lumii/.tattooTemp")
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD)
                .prefixFree("Lumii/.tattooTemp/something")
        )
        confirm(create())
    }

    @Test fun testPhotoEditor() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.brush")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.brush/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.bg").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.bg/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.neon").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.neon/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.mosaic")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.mosaic/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.lightfx")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.lightfx/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.filter")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.filter/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.edited_photo")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.edited_photo/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.eraser")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.eraser/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.sticker")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.photoeditorpro").prefixFree("Photo Editor/.sticker/something")
                .locs(SDCARD)
        )
        confirm(create())
    }

    @Test fun testPhotoCutPaste() = runTest {
        addCandidate(
            neg().pkgs("com.morningshine.autocutpaste").locs(SDCARD).prefixFree("DCIM/Auto Photo Cut Paste/.temp")
        )
        addCandidate(
            pos().pkgs("com.morningshine.autocutpaste").locs(SDCARD)
                .prefixFree("DCIM/Auto Photo Cut Paste/.temp/something")
        )
        confirm(create())
    }

    @Test fun testGenderEditor() = runTest {
        addCandidate(neg().pkgs("com.morningshine.autocutpaste").locs(SDCARD).prefixFree("GenderEditor/temp"))
        addCandidate(pos().pkgs("com.morningshine.autocutpaste").locs(SDCARD).prefixFree("GenderEditor/temp/something"))
        confirm(create())
    }

    @Test fun testGuruVideoMaker() = runTest {
        addCandidate(
            neg().pkgs("videoeditor.videomaker.videoeditorforyoutube").locs(PUBLIC_DATA)
                .prefixFree("Video.Guru/.disk_cache")
        )
        addCandidate(
            pos().pkgs("videoeditor.videomaker.videoeditorforyoutube").locs(PUBLIC_DATA)
                .prefixFree("Video.Guru/.disk_cache/something")
        )
        confirm(create())
    }

    @Test fun testMagicAirbrush() = runTest {
        addCandidate(neg().pkgs("com.magicv.airbrush").locs(SDCARD).prefixFree("AirBrush/.cache"))
        addCandidate(pos().pkgs("com.magicv.airbrush").locs(SDCARD).prefixFree("AirBrush/.cache/something"))
        confirm(create())
    }

    @Test fun testBodyEditor() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.cache").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.cache/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.bg").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.bg/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.filter").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.filter/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.font").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.font/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.log").locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.log/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.sticker").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.sticker/something")
                .locs(SDCARD)
        )
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.tattooTemp")
                .locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.tattooTemp/something")
                .locs(SDCARD)
        )
        confirm(create())
    }

    @Test fun testCutPasteFramesTemp() = runTest {
        addCandidate(
            neg().pkgs("com.zmobileapps.cutpasteframes").locs(SDCARD).prefixFree("DCIM/Cut Paste Frames/.temp")
        )
        addCandidate(
            pos().pkgs("com.zmobileapps.cutpasteframes").locs(SDCARD)
                .prefixFree("DCIM/Cut Paste Frames/.temp/something")
        )
        confirm(create())
    }

    @Test fun testBeautyPlus() = runTest {
        addCandidate(neg().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.videocache"))
        addCandidate(
            pos().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.videocache/something")
        )
        addCandidate(neg().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.temp"))
        addCandidate(pos().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.temp/something"))
        addCandidate(neg().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.cache"))
        addCandidate(pos().pkgs("com.commsource.beautyplus").locs(SDCARD).prefixFree("BeautyPlus/.cache/something"))
        confirm(create())
    }

    @Test fun testAutoCutCut() = runTest {
        addCandidate(neg().pkgs("com.vyroai.AutoCutCut").locs(SDCARD).prefixFree("Pictures/something"))
        addCandidate(neg().pkgs("com.vyroai.AutoCutCut").locs(SDCARD).prefixFree("Pictures/.WebImages"))
        addCandidate(pos().pkgs("com.vyroai.AutoCutCut").locs(SDCARD).prefixFree("Pictures/.WebImages/something"))
        confirm(create())
    }

    @Test fun testRemoveR() = runTest {
        addCandidate(neg().pkgs("remove.unwanted.object").locs(SDCARD).prefixFree("removertemp"))
        addCandidate(pos().pkgs("remove.unwanted.object").locs(SDCARD).prefixFree("removertemp/something"))
        confirm(create())
    }

    @Test fun testB612() = runTest {
        addCandidate(
            neg().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/music")
        )
        addCandidate(
            neg().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/image")
        )
        addCandidate(
            neg().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/filter")
        )
        addCandidate(
            neg().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/sticker")
        )
        addCandidate(
            pos().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/music/something")
        )
        addCandidate(
            pos().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/image/something")
        )
        addCandidate(
            pos().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/filter/something")
        )
        addCandidate(
            pos().pkgs("com.linecorp.b612.android").locs(PUBLIC_DATA)
                .prefixFree("com.linecorp.b612.android/files/sticker/something")
        )
        confirm(create())
    }

    @Test fun testZCamera() = runTest {
        addCandidate(neg().pkgs("com.jb.zcamera").locs(SDCARD).prefixFree("ZCamera/image/cache"))
        addCandidate(pos().pkgs("com.jb.zcamera").locs(SDCARD).prefixFree("ZCamera/image/cache/something"))
        confirm(create())
    }

    @Test fun testInstagramLive() = runTest {
        addCandidate(neg().pkgs("com.instagram.android").locs(SDCARD).prefixFree(".InstagramLive/"))
        addCandidate(neg().pkgs("com.instagram.android").locs(SDCARD).prefixFree(".InstagramLive/files"))
        addCandidate(
            pos().pkgs("com.instagram.android").locs(SDCARD)
                .prefixFree(".InstagramLive/tmp_live_18025560862282799_thumb.jpg")
        )
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

    @Test fun testJustShot() = runTest {
        addCandidate(neg().pkgs("com.ufotosoft.justshot").locs(SDCARD).prefixFree("AlbumCache"))
        addCandidate(pos().pkgs("com.ufotosoft.justshot").locs(SDCARD).prefixFree("AlbumCache/something"))
        confirm(create())
    }

    @Test fun test2Accounts() = runTest {
        addCandidate(
            neg().pkgs("com.excelliance.multiaccount").locs(SDCARD).prefixFree(".dygameres.apps/game_res/3rd/icon")
        )
        addCandidate(
            pos().pkgs("com.excelliance.multiaccount").locs(SDCARD)
                .prefixFree(".dygameres.apps/game_res/3rd/icon/something")
        )
        confirm(create())
    }

    @Test fun testMagiCut() = runTest {
        addCandidate(neg().pkgs("com.energysh.onlinecamera1").locs(SDCARD).prefixFree("newone.pnga"))
        addCandidate(neg().pkgs("com.energysh.onlinecamera1").locs(SDCARD).prefixFree("newone.png/something"))
        addCandidate(neg().pkgs("com.energysh.onlinecamera1").locs(SDCARD).prefixFree("newone"))
        addCandidate(pos().pkgs("com.energysh.onlinecamera1").locs(SDCARD).prefixFree("newone.png"))
        confirm(create())
    }

    @Test fun testZBeautyCamera() = runTest {
        addCandidate(neg().pkgs("com.jb.beautycam").locs(SDCARD).prefixFree("ZBeautyCamera/image/cache"))
        addCandidate(pos().pkgs("com.jb.beautycam").locs(SDCARD).prefixFree("ZBeautyCamera/image/cache/something"))
        confirm(create())
    }

    @Test fun testMeizuDataMigration() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.datamigration").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.datamigration/files/blacklist")
        )
        addCandidate(
            pos().pkgs("com.meizu.datamigration").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.datamigration/files/blacklist/BLACK_LIST_CACHE")
        )
        confirm(create())
    }

    @Test fun testMeizuMusicCover() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.media.music").locs(PUBLIC_DATA).prefixFree("com.meizu.media.music/notify_cover")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.music").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.music/notify_cover/something")
        )
        confirm(create())
    }

    @Test fun testPhotoResizer() = runTest {
        addCandidate(neg().pkgs("com.zmobileapps.photoresizer").locs(SDCARD).prefixFree("temp.jpg"))
        addCandidate(neg().pkgs("com.zmobileapps.photoresizer").locs(SDCARD).prefixFree(".test.jpg"))
        addCandidate(pos().pkgs("com.zmobileapps.photoresizer").locs(SDCARD).prefixFree(".temp.jpg"))
        confirm(create())
    }

    @Test fun testUCMobile() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.media.music").locs(PUBLIC_DATA).prefixFree("com.meizu.media.music/notify_cover")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.music").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.music/notify_cover/something")
        )
        confirm(create())
    }

    @Test fun testInstashot() = runTest {
        addCandidate(
            neg().pkgs("com.camerasideas.instashot").locs(PUBLIC_DATA)
                .prefixFree("com.camerasideas.instashot/files/inshot/.log")
        )
        addCandidate(
            pos().pkgs("com.camerasideas.instashot").locs(PUBLIC_DATA)
                .prefixFree("com.camerasideas.instashot/files/inshot/.log/123")
        )
        addCandidate(
            pos().pkgs("com.camerasideas.instashot").locs(PUBLIC_DATA)
                .prefixFree("com.camerasideas.instashot/files/.temp.jpg")
        )
        confirm(create())
    }

    @Test fun testFightBattle() = runTest {
        addCandidate(neg().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle"))
        addCandidate(pos().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle/.temp.jpg"))
        addCandidate(pos().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle/.temp1.jpg"))
        addCandidate(pos().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle/.temp2.jpg"))
        addCandidate(pos().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle/.smalltemp.jpg"))
        addCandidate(pos().pkgs("best.photo.app.fightbattle").locs(SDCARD).prefixFree("FightBattle/.smalltemp1.jpg"))
        confirm(create())
    }

    @Test fun testMIUIGalleryDiskCache() = runTest {
        addCandidate(neg().pkgs("com.miui.gallery").locs(PUBLIC_DATA).prefixFree("com.miui.gallery/files/gallery_disk"))
        addCandidate(
            pos().pkgs("com.miui.gallery").locs(PUBLIC_DATA)
                .prefixFree("com.miui.gallery/files/gallery_disk_cache/small_size/a800e51a74e4a3383ed8bf47f2d5a33e016c0dbbbf8043bf7b422274f79ced5a.0")
        )
        confirm(create())
    }

    @Test fun testTinnyGIFMaker() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.tinny.gifmaker").locs(SDCARD).prefixFree("TinnyGIFMaker/GIF/.temp"))
        addCandidate(pos().pkgs("com.tinny.gifmaker").locs(SDCARD).prefixFree("TinnyGIFMaker/GIF/.temp/file"))
        confirm(create())
    }

    @Test fun testViskyGallery() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.visky.gallery").locs(SDCARD)
                .prefixFree(".Android/.data/com.visky.gallery.data/.data/.secure/.cache")
        )
        addCandidate(
            pos().pkgs("com.visky.gallery").locs(SDCARD)
                .prefixFree(".Android/.data/com.visky.gallery.data/.data/.secure/.cache/file")
        )
        confirm(create())
    }

    @Test fun testMIUIGallery() = runTest {
        addDefaultNegatives()
        neg("com.miui.gallery", SDCARD, "DCIM/Creative/temp")
        pos("com.miui.gallery", SDCARD, "DCIM/Creative/temp/file")
        neg("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.cache")
        pos("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.cache/$rngString")
        confirm(create())
    }

    @Test fun testHuaweiThemeManager() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.huawei.android.thememanager").locs(SDCARD).prefixFree("Huawei/Themes/something")
        )
        addCandidate(
            pos().pkgs("com.huawei.android.thememanager").locs(SDCARD)
                .prefixFree("Huawei/Themes/.cache/6ba49cfe32916e890491ee101f97424d.thumb")
        )
        addCandidate(
            pos().pkgs("com.huawei.android.thememanager").locs(SDCARD)
                .prefixFree("Huawei/Themes/.cache/Explorer.hwt/preview/icon_small.jpg")
        )
        addCandidate(
            pos().pkgs("com.huawei.android.thememanager").locs(SDCARD)
                .prefixFree("Huawei/Themes/.cache/Explorer.hwt")
        )
        confirm(create())
    }

    @Test fun testFaceMoji() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.facemoji.lite.xiaomi").locs(PUBLIC_DATA)
                .prefixFree("com.facemoji.lite.xiaomi/files/okhttp_cache")
        )
        addCandidate(
            pos().pkgs("com.facemoji.lite.xiaomi").locs(PUBLIC_DATA)
                .prefixFree("com.facemoji.lite.xiaomi/files/okhttp_cache1798737084")
        )
        addCandidate(
            pos().pkgs("com.facemoji.lite.xiaomi").locs(PUBLIC_DATA)
                .prefixFree("com.facemoji.lite.xiaomi/files/okhttp_cacheany")
        )
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