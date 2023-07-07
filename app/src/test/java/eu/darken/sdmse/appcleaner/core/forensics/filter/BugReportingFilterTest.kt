package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.addCandidate
import eu.darken.sdmse.appcleaner.core.forensics.locs
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pkgs
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.appcleaner.core.forensics.prefixFree
import eu.darken.sdmse.common.areas.DataArea.Type.DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BugReportingFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = BugReportingFilter(
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    @Test fun testFilterFabric() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json")
        )
        addCandidate(
            neg().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
        )
        addCandidate(neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.Fabric"))
        addCandidate(neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files"))
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A330373-0001-3113-B40FA339CFB5SessionDevice.cls")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5BeginSession.cls")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5SessionApp.cls")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/571829D30162-0001-773C-9E7740B51B59user.meta")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5SessionOS.cls")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/log-files/crashlytics-userlog-5655D3DD0195-0002-6DD3-D2E9215735D2.temp")
        )

        confirm(create())
    }

    @Test fun testFilterFirefox() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.firefox", "org.mozilla.firefox_beta")
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox_beta/files/mozilla/Crash Reports")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox_beta/files/mozilla/Crash Reports/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.firefox/files/mozilla/Crash Reports")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox/files/mozilla/Crash Reports/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/minidumps")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree(
                "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/minidumps/$rngString"
            )
        )

        confirm(create())
    }

    @Test fun testFilterFireFoxNightly() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.fennec_aurora")
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.fennec_aurora/files/mozilla/Crash Reports")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.fennec_aurora/files/mozilla/Crash Reports/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree(
                "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps/$rngString"
            )
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree(
                "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes/$rngString"
            )
        )

        confirm(create())
    }

    @Test fun testFilterMozillaFocus() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.focus")
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.focus/files/mozilla/Crash Reports")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.focus/files/mozilla/Crash Reports/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree(
                "org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps/$rngString"
            )
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterMozillaClone() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("strawberry")
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("strawberry/files/mozilla/Crash Reports")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("strawberry/files/mozilla/Crash Reports/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("strawberry/files/mozilla/sqqj1c1o.default/minidumps")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("strawberry/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("strawberry/files/mozilla/sqqj1c1o.default/crashes")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA)
                .prefixFree("strawberry/files/mozilla/sqqj1c1o.default/crashes/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterShell() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.android.shell").locs(PRIVATE_DATA)
                .prefixFree("com.android.shell/files/bugreports/")
        )
        addCandidate(
            pos().pkgs("com.android.shell").locs(PRIVATE_DATA)
                .prefixFree("com.android.shell/files/bugreports/bugreport-dasdasdasd")
        )

        confirm(create())
    }

    @Test fun testFilterSplashTop() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.splashtop.remote.pad.v2").locs(SDCARD).prefixFree("Splashtop.log"))
        addCandidate(
            pos().pkgs("com.splashtop.remote.pad.v2").locs(SDCARD).prefixFree("Splashtop.log.12312312312")
        )

        confirm(create())
    }

    @Test fun testFilterGoWiper() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.gowiper.android").locs(PUBLIC_DATA).prefixFree("com.gowiper.android/files/logs")
        )
        addCandidate(
            pos().pkgs("com.gowiper.android").locs(PUBLIC_DATA)
                .prefixFree("com.gowiper.android/files/logs/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterIranApp() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("ir.tgbs.android.iranapp").locs(PUBLIC_DATA)
                .prefixFree("ir.tgbs.android.iranapp/files/log")
        )
        addCandidate(
            pos().pkgs("ir.tgbs.android.iranapp").locs(PUBLIC_DATA)
                .prefixFree("ir.tgbs.android.iranapp/files/log/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterLauncherEx() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.gau.go.launcherex", "com.gau.go.launcherex.os")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("GOLauncherEX/log"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("GOLauncherEXOS/log"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("GOLauncherEX/log/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("GOLauncherEXOS/log/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterCleanMasterGuard() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/logs")
        )
        addCandidate(
            neg().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/minidump")
        )
        addCandidate(
            pos().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/logs/$rngString")
        )
        addCandidate(
            pos().pkgs("com.cleanmaster.mguard").locs(PUBLIC_DATA)
                .prefixFree("com.cleanmaster.mguard/files/minidump/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterYahoo() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf(
            "com.yahoo.mobile.client.android.atom",
            "com.yahoo.mobile.client.android.mail",
            "com.yahoo.mobile.client.android.yeti",
            "com.yahoo.mobile.client.android.ecauction",
            "com.yahoo.mobile.client.android.fantasyfootball",
            "com.yahoo.mobile.client.android.search",
            "com.yahoo.mobile.client.android.finance",
            "com.yahoo.mobile.client.android.im",
            "com.yahoo.mobile.client.android.flickr",
            "com.yahoo.mobile.client.android.weather",
            "com.yahoo.mobile.client.android.cricket"
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/atom/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/atom/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/mail/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/mail/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/yeti/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/yeti/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/ecauction/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/ecauction/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/fantasyfootball/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/fantasyfootball/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/search/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/search/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/finance/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/finance/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/im/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/im/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/flickr/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/flickr/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/weather/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/weather/Debug/$rngString")
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/cricket/Debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/cricket/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.atom/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.atom/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.mail/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.mail/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.yeti/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.yeti/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.ecauction/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.ecauction/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.fantasyfootball/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree(
                "yahoo/com.yahoo.mobile.client.android.fantasyfootball/Debug/$rngString"
            )
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.search/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.search/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.finance/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.finance/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.im/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.im/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.flickr/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.flickr/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.weather/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.weather/Debug/$rngString")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(SDCARD).prefixFree("yahoo/com.yahoo.mobile.client.android.cricket/Debug")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("yahoo/com.yahoo.mobile.client.android.cricket/Debug/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterAudials() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.audials", "com.audials.paid")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("Audials/log"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("Audials/log/$rngString"))

        confirm(create())
    }

    @Test fun testFilterTapatalk() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.quoord.tapatalkpro.activity").locs(SDCARD).prefixFree("tapatalkLog"))
        addCandidate(
            pos().pkgs("com.quoord.tapatalkpro.activity").locs(SDCARD)
                .prefixFree("tapatalkLog/123-4567-Log.txt")
        )

        confirm(create())
    }

    @Test fun testFilterAmazonMp3() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.amazon.mp3", "com.amazon.bueller.music")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("amazonmp3/temp"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("amazonmp3/temp/log.txt"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("amazonmp3/temp/AMPmetrics_v2.txt"))

        confirm(create())
    }

    @Test fun testFilterMobileCare() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.frostwire.android").locs(SDCARD).prefixFree("FrostWire/.azureus/logs"))
        addCandidate(
            pos().pkgs("com.frostwire.android").locs(SDCARD).prefixFree("FrostWire/.azureus/logs/debug_4.log")
        )
        addCandidate(
            pos().pkgs("com.frostwire.android").locs(SDCARD).prefixFree("FrostWire/.azureus/logs/UPnP_4.log")
        )

        confirm(create())
    }

    @Test fun testFilterSkype() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/something/logs")
        )
        addCandidate(
            neg().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/something/logs")
        )
        addCandidate(
            pos().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/something/logs/$rngString")
        )
        addCandidate(
            pos().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.rover/files/something/logs/$rngString")
        )
        addCandidate(
            pos().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/something/logs/$rngString")
        )
        addCandidate(
            pos().pkgs("com.skype.rover", "com.skype.raider").locs(PRIVATE_DATA)
                .prefixFree("com.skype.raider/files/something/logs/$rngString")
        )

        confirm(create())
    }

    @Test fun testFilterKanjianMusic() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.kanjian.music").locs(SDCARD).prefixFree("com.kanjian.radio_.log"))
        addCandidate(
            pos().pkgs("com.kanjian.music").locs(SDCARD).prefixFree("com.kanjian.radio_123123123.log")
        )

        confirm(create())
    }

    @Test fun testApp2SD() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("in.co.pricealert.apps2sd").locs(DATA)
                .prefixFree("apps2sd-log/$rngString")
        )
        addCandidate(neg().pkgs("in.co.pricealert.apps2sd").locs(DATA).prefixFree("apps2sd-mount-script.log"))
        addCandidate(
            pos().pkgs("in.co.pricealert.apps2sd").locs(DATA)
                .prefixFree("apps2sd-log/apps2sd-mount-script.log")
        )

        confirm(create())
    }

    @Test fun testLogsFilter() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Logs"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/logs"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/logfiles"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Logfiles"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Log"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/log"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Logs"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/logs"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/logfiles"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Logfiles"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Log"))
        addCandidate(neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/log"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/Logs/file"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/logs/file"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/logfiles/file"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/Logfiles/file"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/Log/file"))
        addCandidate(neg().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/files/log/file"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Logs/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/logs/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/logfiles/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Logfiles/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/Log/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(SDCARD).prefixFree("someapp/log/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Logs/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/logs/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/logfiles/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Logfiles/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/Log/$rngString"))
        addCandidate(pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("someapp/files/log/$rngString"))

        confirm(create())
    }

    @Test fun testMaleWorkout() = runTest {
        addCandidate(
            neg().pkgs("homeworkout.homeworkouts.noequipment").locs(SDCARD).prefixFree("MaleWorkout/crash")
        )
        addCandidate(
            pos().pkgs("homeworkout.homeworkouts.noequipment").locs(SDCARD)
                .prefixFree("MaleWorkout/crash/$rngString")
        )

        confirm(create())
    }

    @Test fun testKingSoftOffice() = runTest {
        val pkgs = arrayOf(
            "cn.wps.moffice_i18n",
            "cn.wps.moffice_i18n_hd",
            "cn.wps.moffice",
            "cn.wps.moffice_eng"
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PUBLIC_DATA).prefixFree("cn.wps.moffice_eng/.cache/KingsoftOffice")
        )
        addCandidate(
            neg().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("cn.wps.moffice_eng/.cache/KingsoftOffice/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("cn.wps.moffice_eng/.cache/KingsoftOffice/log/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("cn.wps.moffice_eng/.cache/KingsoftOffice/.temp/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("cn.wps.moffice/.cache/KingsoftOffice/log/$rngString")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PUBLIC_DATA)
                .prefixFree("cn.wps.moffice/.cache/KingsoftOffice/.temp/$rngString")
        )

        confirm(create())
    }

    @Test fun testArchosDebug() = runTest {
        val pkgs = arrayOf(
            "com.archos.mediacenter.videofree",
            "com.archos.mediacenter.video"
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("archos_debug"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD).prefixFree("archos_debug/$rngString")
        )

        confirm(create())
    }

    @Test fun testTencentMicroMsg() = runTest {
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg"))
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        )
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/crash"))
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/crash/$rngString")
        )
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/xlog"))
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/xlog/$rngString")
        )
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/locallog"))
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/locallog/$rngString")
        )
        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/watchdog"))
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/watchdog/$rngString")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/failmsgfilecache")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/failmsgfilecache/$rngString")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/FTS5IndexMicroMsgInfo.txt")
        )
        addCandidate(
            neg().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/logcat")
        )
        addCandidate(
            pos().pkgs("com.tencent.mm").locs(SDCARD)
                .prefixFree("tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/logcat/$rngString")
        )

        confirm(create())
    }

    @Test fun testAirDroid() = runTest {
        addCandidate(neg().pkgs("com.sand.airdroid").locs(SDCARD).prefixFree("AirDroid"))
        addCandidate(pos().pkgs("com.sand.airdroid").locs(SDCARD).prefixFree("AirDroid/exception.log"))

        confirm(create())
    }

    @Test fun testTencentEncryptedLogs() = runTest {
        addCandidate(neg().pkgs("some.pkg").locs(SDCARD).prefixFree("tencent/wns/EncryptLogs/some.pkg"))
        addCandidate(
            pos().pkgs("some.pkg").locs(SDCARD).prefixFree("tencent/wns/EncryptLogs/some.pkg/11-1111")
        )
        addCandidate(
            pos().pkgs("some.pkg").locs(SDCARD)
                .prefixFree("tencent/wns/EncryptLogs/some.pkg/11-1111/1.wns.log")
        )

        confirm(create())
    }

    @Test fun testTencentMsflogs() = runTest {
        addCandidate(neg().pkgs("some.test.pkg").locs(SDCARD).prefixFree("tencent/msflogs"))
        addCandidate(neg().pkgs("some.test.pkg").locs(SDCARD).prefixFree("tencent/msflogs/some"))
        addCandidate(neg().pkgs("some.test.pkg").locs(SDCARD).prefixFree("tencent/msflogs/some/test"))
        addCandidate(neg().pkgs("some.test.pkg").locs(SDCARD).prefixFree("tencent/msflogs/some/test/pkg"))
        addCandidate(
            pos().pkgs("some.test.pkg").locs(SDCARD).prefixFree("tencent/msflogs/some/test/pkg/something")
        )

        confirm(create())
    }

    @Test fun testMusicolet() = runTest {
        addCandidate(neg().pkgs("in.krosbits.musicolet").locs(SDCARD).prefixFree("Musicolet"))
        addCandidate(neg().pkgs("in.krosbits.musicolet").locs(SDCARD).prefixFree("Musicolet/logs"))
        addCandidate(neg().pkgs("in.krosbits.musicolet").locs(SDCARD).prefixFree("Musicolet/logs/.nomedia"))
        addCandidate(pos().pkgs("in.krosbits.musicolet").locs(SDCARD).prefixFree("Musicolet/logs/last.crash"))

        confirm(create())
    }

    @Test fun testDuplicateFileRemover() = runTest {
        val pkg = "com.duplicatefile.remover.duplicatefilefinder.duplicatefileremover"
        addCandidate(neg().pkgs(pkg).locs(SDCARD).prefixFree("DuplicateFileRemover"))
        addCandidate(neg().pkgs(pkg).locs(SDCARD).prefixFree("DuplicateFileRemover/strawberry"))
        addCandidate(pos().pkgs(pkg).locs(SDCARD).prefixFree("DuplicateFileRemover/log/something"))

        confirm(create())
    }

    @Test fun testGearLog() = runTest {
        val pkg = "com.samsung.android.app.watchmanager"
        addCandidate(neg().pkgs(pkg).locs(SDCARD).prefixFree("log/GearLog"))
        addCandidate(pos().pkgs(pkg).locs(SDCARD).prefixFree("log/GearLog/dumpState-UHM.log"))
        addCandidate(
            pos().pkgs(pkg).locs(SDCARD).prefixFree("log/GearLog/dumpState_FOTA_PROVIDER_GEARO0.log")
        )

        confirm(create())
    }

    @Test fun testMozillaFenix() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.fenix")
        addCandidate(neg().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/gv_measurements"))
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/gv_measurements.json")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/gv_measurements-0.json")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/gv_measurements-1.json")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/gv_measurements-22.json")
        )
        addCandidate(neg().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/glean_data"))
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/glean_data/pending_pings")
        )
        addCandidate(pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/glean_data/events"))
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/glean_data/events/events")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(PRIVATE_DATA).prefixFree("org.mozilla.fenix/glean_data/glean_already_ran")
        )

        confirm(create())
    }

    @Test fun testTencentTbs() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.x.browser.x5", "com.tencent.something")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("tbs"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("tbs/.logTmp"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("tbs/.logTmp/something"))

        confirm(create())
    }

    @Test fun testBixby() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.samsung.android.bixby.service")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("log"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("log/0_something"))
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("log/0_com.samsung.android.bixby.service_bixbysearch_index.log.lck")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("log/0_com.samsung.android.bixby.service_bixbysearch_index.log")
        )
        addCandidate(
            pos().pkgs(*pkgs).locs(SDCARD)
                .prefixFree("log/0_com.samsung.android.bixby.service/some other stuff")
        )

        confirm(create())
    }

    @Test fun testSmartHome() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.xiaomi.smarthome", "com.xiaomi.something")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("wifi_config"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("wifi_config.log"))

        confirm(create())
    }

    @Test fun testICBCWAPLog() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.icbc")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("ICBCWAPLog"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("ICBCWAPLog/junk.log"))

        confirm(create())
    }

    @Test fun testWDFileHub() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("filehubplus.wd.activities")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("FileHub Plus/log"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("FileHub Plus/log/something"))

        confirm(create())
    }

    @Test fun testVMLOG() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.vmos.glb")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("vmlog/"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("vmlog/something"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("log.tx"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("log.txt/test"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("log.txt"))

        confirm(create())
    }

    @Test fun testMicrosoftFeedback() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.microsoft.emmx")
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("Pictures"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("Pictures/"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("Pictures/Microsoft Edge feedback.jpg"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("Pictures/Microsoft Edge feedback (1).jpg"))

        confirm(create())
    }

    @Test fun testLuumi() = runTest {
        addCandidate(
            neg().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD).prefixFree("Lumii/.log")
        )
        addCandidate(
            pos().pkgs("photo.editor.photoeditor.filtersforpictures").locs(SDCARD)
                .prefixFree("Lumii/.log/something")
        )

        confirm(create())
    }

    @Test fun testBodyEditor() = runTest {
        addCandidate(
            neg().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.log").locs(SDCARD)
        )
        addCandidate(
            pos().pkgs("breastenlarger.bodyeditor.photoeditor").prefixFree("Body Editor/.log/something")
                .locs(SDCARD)
        )

        confirm(create())
    }

    @Test fun testMeizuFlyme() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.flyme.service.find").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.service.find/files")
        )
        addCandidate(
            neg().pkgs("com.meizu.flyme.service.find").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.service.find/files/log")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.service.find").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.service.find/files/log.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuPPS() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.meizu.pps").locs(PUBLIC_DATA).prefixFree("dcms"))
        addCandidate(neg().pkgs("com.meizu.pps").locs(PUBLIC_DATA).prefixFree("dcms/log"))
        addCandidate(pos().pkgs("com.meizu.pps").locs(PUBLIC_DATA).prefixFree("dcms/log/test"))

        confirm(create())
    }

    @Test fun testFlymeSyncLog() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.meizu.mzsyncservice").locs(SDCARD).prefixFree("Android"))
        addCandidate(neg().pkgs("com.meizu.mzsyncservice").locs(SDCARD).prefixFree("Android/something"))
        addCandidate(
            neg().pkgs("com.meizu.mzsyncservice").locs(SDCARD).prefixFree("Android/flyme_sync_sdk_log.txt12")
        )
        addCandidate(
            pos().pkgs("com.meizu.mzsyncservice").locs(SDCARD).prefixFree("Android/flyme_sync_sdk_log.txt")
        )
        addCandidate(
            pos().pkgs("com.meizu.mzsyncservice").locs(SDCARD)
                .prefixFree("Android/flyme_sync_sdk_log.txt/logs_v2.txt")
        )
        addCandidate(
            pos().pkgs("com.meizu.mzsyncservice").locs(SDCARD)
                .prefixFree("Android/flyme_sync_sdk_log.txt/something")
        )
        addCandidate(
            neg().pkgs("com.meizu.mzsyncservice").locs(PUBLIC_DATA).prefixFree("com.meizu.mzsyncservice")
        )
        addCandidate(
            pos().pkgs("com.meizu.mzsyncservice").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.mzsyncservice/2020-09-19.log.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuCustomizer() = runTest {
        addCandidate(neg().pkgs("com.meizu.customizecenter").locs(SDCARD).prefixFree("Customize/Log"))
        addCandidate(pos().pkgs("com.meizu.customizecenter").locs(SDCARD).prefixFree("Customize/Log/test"))
        addCandidate(pos().pkgs("com.meizu.customizecenter").locs(SDCARD).prefixFree("Customize/Log/1234567"))

        confirm(create())
    }

    @Test fun testAndroidBrowser() = runTest {
        addCandidate(neg().pkgs("com.android.browser").locs(PUBLIC_DATA).prefixFree("com.android.browser"))
        addCandidate(
            pos().pkgs("com.android.browser").locs(PUBLIC_DATA).prefixFree("com.android.browser/gslb_sdk_log")
        )
        addCandidate(
            pos().pkgs("com.android.browser").locs(PUBLIC_DATA)
                .prefixFree("com.android.browser/update_component_log")
        )
        addCandidate(
            neg().pkgs("com.android.browser").locs(PUBLIC_DATA).prefixFree("com.android.browser/files")
        )
        addCandidate(
            pos().pkgs("com.android.browser").locs(PUBLIC_DATA)
                .prefixFree("com.android.browser/files/usage_logs_v2.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuToolbox() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.flyme.toolbox").locs(PUBLIC_DATA).prefixFree("com.meizu.flyme.toolbox")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.toolbox").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.toolbox/update_component_log")
        )

        confirm(create())
    }

    @Test fun testDuplicatesCleaner() = runTest {
        addCandidate(neg().pkgs("com.kaerosduplicatescleaner").locs(SDCARD).prefixFree("KaerosLogs"))
        addCandidate(
            pos().pkgs("com.kaerosduplicatescleaner").locs(SDCARD).prefixFree("KaerosLogs/something")
        )

        confirm(create())
    }

    @Test fun testMusicFx() = runTest {
        addCandidate(neg().pkgs("com.android.musicfx").locs(PUBLIC_DATA).prefixFree("com.android.musicfx"))
        addCandidate(
            pos().pkgs("com.android.musicfx").locs(PUBLIC_DATA).prefixFree("com.android.musicfx/gslb_log.txt")
        )

        confirm(create())
    }

    @Test fun testFlyMeService() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.flyme.service.find").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.service.find/files")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.service.find").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.service.find/files/log.txt")
        )

        confirm(create())
    }

    @Test fun testFlyMeWeather() = runTest {
        addCandidate(
            neg().pkgs("com.meizu.flyme.weather").locs(PUBLIC_DATA).prefixFree("com.meizu.flyme.weather")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.weather").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.weather/weather_log.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuAccount() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.meizu.account").locs(PUBLIC_DATA).prefixFree("com.meizu.account"))
        addCandidate(
            pos().pkgs("com.meizu.account").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.account/2020-09-19.log.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuInput() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.flyme.input").locs(PUBLIC_DATA).prefixFree("com.meizu.flyme.input")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.input").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.input/2020-09-19.log.txt")
        )

        confirm(create())
    }

    @Test fun testMeizuVideo() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.media.video").locs(PUBLIC_DATA).prefixFree("com.meizu.media.video")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.video").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.video/update_component_plugin_log")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.video").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.video/update_component_plugin_log/something")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.video").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.video/update_component_log")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.video").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.media.video/update_component_log/something")
        )

        confirm(create())
    }

    @Test fun testFlymeCorelog() = runTest {
        addDefaultNegatives()

        val pkgs = arrayOf(
            "com.meizu.flyme.input",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi"
        )

        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("sogou"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree("sogou/corelog"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("sogou/corelog/activity_mini.txt"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree("sogou/corelog/activity.txt"))


        confirm(create())
    }

    @Test fun testVideoEditorPro() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.videoeditorpro.android").locs(SDCARD).prefixFree("logger/logs_"))
        addCandidate(pos().pkgs("com.videoeditorpro.android").locs(SDCARD).prefixFree("logger/logs_1.csv"))
        addCandidate(pos().pkgs("com.videoeditorpro.android").locs(SDCARD).prefixFree("logger/logs_2.csv"))

        confirm(create())
    }

    @Test fun testFlyMeUpgradeLog() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.flyme.update").locs(PUBLIC_DATA)
                .prefixFree("com.meizu.flyme.update/app_upgrade_l")
        )
        addCandidate(
            pos().pkgs("com.meizu.flyme.update").locs(SDCARD)
                .prefixFree("com.meizu.flyme.update/app_upgrade_log")
        )

        confirm(create())
    }

    @Test fun testMofficeLogs() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("cn.wps.moffice_eng").locs(PUBLIC_DATA).prefixFree("cn.wps.moffice_eng/files/klog")
        )
        addCandidate(
            pos().pkgs("cn.wps.moffice_eng").locs(PUBLIC_DATA).prefixFree("cn.wps.moffice_eng/files/klog/1")
        )

        confirm(create())
    }

    @Test fun testMiPushLog() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/MiPushLog")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/MiPushLog/log.lock")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/MiPushLog/log1.txt")
        )

        confirm(create())
    }

    @Test fun testXMSFLogs() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA).prefixFree("com.xiaomi.xmsf/files/dump"))
        addCandidate(
            neg().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA).prefixFree("com.xiaomi.xmsf/files/dump/something")
        )
        addCandidate(
            pos().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA).prefixFree("com.xiaomi.xmsf/files/dump/xmsf.log")
        )
        addCandidate(
            pos().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA).prefixFree("com.xiaomi.xmsf/files/dump/xmsf.log.")
        )
        addCandidate(
            pos().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA)
                .prefixFree("com.xiaomi.xmsf/files/dump/xmsf.log.1")
        )
        addCandidate(
            pos().pkgs("com.xiaomi.xmsf").locs(PUBLIC_DATA).prefixFree("com.xiaomi.xmsf/files/dump/abc.log.1")
        )

        confirm(create())
    }

    @Test fun testMIUIBugReport() = runTest {
        addDefaultNegatives()
        neg("com.miui.bugreport", SDCARD, "MIUI/debug_log")
        pos("com.miui.bugreport", SDCARD, "MIUI/debug_log/bugrepor")
        pos("com.miui.bugreport", SDCARD, "MIUI/debug_log/bugreport-2021-03-15-030946.zip")
        pos("com.miui.bugreport", SDCARD, "MIUI/debug_log/powerinfo/result_reason")
        pos(
            "com.miui.bugreport",
            SDCARD,
            "MIUI/debug_log/com.miui.bugreport/cache/image/screenshot_0_1618216975622.jpg"
        )

        confirm(create())
    }

    @Test fun testMIUIGalleryVlog() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.miui.gallery").locs(PUBLIC_DATA).prefixFree("com.miui.gallery/files/vlog")
        )
        addCandidate(
            pos().pkgs("com.miui.gallery").locs(PUBLIC_DATA).prefixFree("com.miui.gallery/files/vlog/files")

        )

        confirm(create())
    }

    @Test fun testMIUILoggerUI() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.debug.loggerui").locs(SDCARD).prefixFree("debuglogger"))
        addCandidate(pos().pkgs("com.debug.loggerui").locs(SDCARD).prefixFree("debuglogger/files"))

        confirm(create())
    }


    @Test fun tencentXlog() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("video.like.lite").locs(PUBLIC_DATA).prefixFree("video.like.lite/files/xlo")
        )
        addCandidate(
            neg().pkgs("video.like.lite").locs(PUBLIC_DATA).prefixFree("video.like.lite/files/xlog")
        )
        addCandidate(
            neg().pkgs("video.like.lite").locs(PUBLIC_DATA).prefixFree("video.like.lite/cache/xlo")
        )
        addCandidate(
            neg().pkgs("video.like.lite").locs(PUBLIC_DATA).prefixFree("video.like.lite/cache/xlog")
        )

        addCandidate(
            pos().pkgs("video.like.lite").locs(PUBLIC_DATA).prefixFree("video.like.lite/files/xlog/5381")

        )

        confirm(create())
    }

    @Test fun `meizu contacts db log`() = runTest {
        addDefaultNegatives()

        neg("android", SDCARD, "Android")
        neg("android", SDCARD, "Android/contacts_db_log.txt")
        neg("com.android.providers.contacts", SDCARD, "Android")
        pos("com.android.providers.contacts", SDCARD, "Android/contacts_db_log.txt")

        confirm(create())
    }

    @Test fun `meizu PPS dumps`() = runTest {
        addDefaultNegatives()

        neg("com.meizu.pps", SDCARD, "PPS")
        neg("com.meizu.pps", SDCARD, "PPS/something")
        pos("com.meizu.pps", SDCARD, "PPS/2020-08-06.txt")

        confirm(create())
    }

    @Test fun `PicsArt debug logs`() = runTest {
        addDefaultNegatives()

        neg("com.picsart.studio", SDCARD, "Download")
        neg("com.picsart.studio", SDCARD, "Download/file")
        pos("com.picsart.studio", SDCARD, "Download/crash_log_1.txt")
        pos("com.picsart.studio", SDCARD, "Download/crash_log_2.txt")

        confirm(create())
    }

    @Test fun `Viber debug logs`() = runTest {
        addDefaultNegatives()

        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.logs")
        neg("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.logs/.nomedia")
        pos("com.viber.voip", PUBLIC_DATA, "com.viber.voip/files/.logs/$rngString")

        confirm(create())
    }

    /**
     * https://github.com/FBlackBox/BlackBox/issues/89
     * https://github.com/wangjintao/TLog
     */
    @Test fun `chinese logging library`() = runTest {
        addDefaultNegatives()

        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9")
        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9/.nomedia")
        pos("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9/$rngString")

        confirm(create())
    }
}