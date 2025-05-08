package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
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
        jsonBasedSieveFactory = createJsonSieveFactory(),
        environment = storageEnvironment,
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test fabric crashlytics files`() = runTest {
        addDefaultNegatives()
        neg(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json"
        )
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A330373-0001-3113-B40FA339CFB5SessionDevice.cls"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5BeginSession.cls"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5SessionApp.cls"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/571829D30162-0001-773C-9E7740B51B59user.meta"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/57176A5E0162-0001-3571-B40FA339CFB5SessionOS.cls"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android.crashlytics-core/log-files/crashlytics-userlog-5655D3DD0195-0002-6DD3-D2E9215735D2.temp"
        )

        confirm(create())
    }

    @Test fun `test firefox crash reports`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.firefox", "org.mozilla.firefox_beta")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox_beta/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox_beta/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.firefox_beta/files/mozilla/sqqj1c1o.default/minidumps/$rngString")

        confirm(create())
    }

    @Test fun `test firefox nightly crash reports`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.fennec_aurora")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test mozilla focus crash reports`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.focus")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test mozilla clone crash reports`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("strawberry")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test shell bug reports`() = runTest {
        addDefaultNegatives()
        neg("com.android.shell", PRIVATE_DATA, "com.android.shell/files/bugreports/")
        pos("com.android.shell", PRIVATE_DATA, "com.android.shell/files/bugreports/bugreport-dasdasdasd")

        confirm(create())
    }

    @Test fun `test splashtop logs`() = runTest {
        addDefaultNegatives()
        neg("com.splashtop.remote.pad.v2", SDCARD, "Splashtop.log")
        pos("com.splashtop.remote.pad.v2", SDCARD, "Splashtop.log.12312312312")

        confirm(create())
    }

    @Test fun `test filter firefox nightly`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.fennec_aurora")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fennec_aurora/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test filter mozilla focus`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.focus")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.focus/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test filter mozilla clone`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("strawberry")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/Crash Reports")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/Crash Reports/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/minidumps")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/minidumps/$rngString")
        neg(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/crashes")
        pos(pkgs[0], PRIVATE_DATA, "strawberry/files/mozilla/sqqj1c1o.default/crashes/$rngString")

        confirm(create())
    }

    @Test fun `test filter shell`() = runTest {
        addDefaultNegatives()
        neg("com.android.shell", PRIVATE_DATA, "com.android.shell/files/bugreports/")
        pos("com.android.shell", PRIVATE_DATA, "com.android.shell/files/bugreports/bugreport-dasdasdasd")

        confirm(create())
    }

    @Test fun `test filter splash top`() = runTest {
        addDefaultNegatives()
        neg("com.splashtop.remote.pad.v2", SDCARD, "Splashtop.log")
        pos("com.splashtop.remote.pad.v2", SDCARD, "Splashtop.log.12312312312")

        confirm(create())
    }

    @Test fun `test filter go wiper`() = runTest {
        addDefaultNegatives()
        neg("com.gowiper.android", PUBLIC_DATA, "com.gowiper.android/files/logs")
        pos("com.gowiper.android", PUBLIC_DATA, "com.gowiper.android/files/logs/$rngString")

        confirm(create())
    }

    @Test fun `test filter iran app`() = runTest {
        addDefaultNegatives()
        neg("ir.tgbs.android.iranapp", PUBLIC_DATA, "ir.tgbs.android.iranapp/files/log")
        pos("ir.tgbs.android.iranapp", PUBLIC_DATA, "ir.tgbs.android.iranapp/files/log/$rngString")

        confirm(create())
    }

    @Test fun `test filter launcher ex`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.gau.go.launcherex", "com.gau.go.launcherex.os")
        neg(pkgs[0], SDCARD, "GOLauncherEX/log")
        neg(pkgs[0], SDCARD, "GOLauncherEXOS/log")
        pos(pkgs[0], SDCARD, "GOLauncherEX/log/$rngString")
        pos(pkgs[0], SDCARD, "GOLauncherEXOS/log/$rngString")

        confirm(create())
    }

    @Test fun `test filter clean master guard`() = runTest {
        addDefaultNegatives()
        neg("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/logs")
        neg("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/minidump")
        pos("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/logs/$rngString")
        pos("com.cleanmaster.mguard", PUBLIC_DATA, "com.cleanmaster.mguard/files/minidump/$rngString")

        confirm(create())
    }

    @Test fun `test filter yahoo`() = runTest {
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
        neg(pkgs[0], SDCARD, "yahoo/atom/Debug")
        pos(pkgs[0], SDCARD, "yahoo/atom/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/mail/Debug")
        pos(pkgs[0], SDCARD, "yahoo/mail/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/yeti/Debug")
        pos(pkgs[0], SDCARD, "yahoo/yeti/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/ecauction/Debug")
        pos(pkgs[0], SDCARD, "yahoo/ecauction/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/fantasyfootball/Debug")
        pos(pkgs[0], SDCARD, "yahoo/fantasyfootball/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/search/Debug")
        pos(pkgs[0], SDCARD, "yahoo/search/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/finance/Debug")
        pos(pkgs[0], SDCARD, "yahoo/finance/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/im/Debug")
        pos(pkgs[0], SDCARD, "yahoo/im/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/flickr/Debug")
        pos(pkgs[0], SDCARD, "yahoo/flickr/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/weather/Debug")
        pos(pkgs[0], SDCARD, "yahoo/weather/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/cricket/Debug")
        pos(pkgs[0], SDCARD, "yahoo/cricket/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.atom/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.atom/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.mail/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.mail/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.yeti/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.yeti/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.ecauction/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.ecauction/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.fantasyfootball/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.fantasyfootball/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.search/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.search/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.finance/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.finance/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.im/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.im/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.flickr/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.flickr/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.weather/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.weather/Debug/$rngString")
        neg(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.cricket/Debug")
        pos(pkgs[0], SDCARD, "yahoo/com.yahoo.mobile.client.android.cricket/Debug/$rngString")

        confirm(create())
    }

    @Test fun `test filter audials`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.audials", "com.audials.paid")
        neg(pkgs[0], SDCARD, "Audials/log")
        pos(pkgs[0], SDCARD, "Audials/log/$rngString")

        confirm(create())
    }

    @Test fun `test filter tapatalk`() = runTest {
        addDefaultNegatives()
        neg("com.quoord.tapatalkpro.activity", SDCARD, "tapatalkLog")
        pos("com.quoord.tapatalkpro.activity", SDCARD, "tapatalkLog/123-4567-Log.txt")

        confirm(create())
    }

    @Test fun `test filter amazon mp3`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.amazon.mp3", "com.amazon.bueller.music")
        neg(pkgs[0], SDCARD, "amazonmp3/temp")
        pos(pkgs[0], SDCARD, "amazonmp3/temp/log.txt")
        pos(pkgs[0], SDCARD, "amazonmp3/temp/AMPmetrics_v2.txt")

        confirm(create())
    }

    @Test fun `test filter mobile care`() = runTest {
        addDefaultNegatives()
        neg("com.frostwire.android", SDCARD, "FrostWire/.azureus/logs")
        pos("com.frostwire.android", SDCARD, "FrostWire/.azureus/logs/debug_4.log")
        pos("com.frostwire.android", SDCARD, "FrostWire/.azureus/logs/UPnP_4.log")

        confirm(create())
    }

    @Test fun `test filter skype`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.skype.rover", "com.skype.raider")
        neg(pkgs[0], PRIVATE_DATA, "com.skype.rover/files/something/logs")
        neg(pkgs[1], PRIVATE_DATA, "com.skype.raider/files/something/logs")
        pos(pkgs[0], PRIVATE_DATA, "com.skype.rover/files/something/logs/$rngString")
        pos(pkgs[0], PRIVATE_DATA, "com.skype.rover/files/something/logs/$rngString")
        pos(pkgs[1], PRIVATE_DATA, "com.skype.raider/files/something/logs/$rngString")
        pos(pkgs[1], PRIVATE_DATA, "com.skype.raider/files/something/logs/$rngString")

        confirm(create())
    }

    @Test fun `test kanjian music`() = runTest {
        addDefaultNegatives()
        neg("com.kanjian.music", SDCARD, "com.kanjian.radio_.log")
        pos("com.kanjian.music", SDCARD, "com.kanjian.radio_123123123.log")

        confirm(create())
    }

    @Test fun `test app2sd`() = runTest {
        addDefaultNegatives()
        neg("in.co.pricealert.apps2sd", DATA, "apps2sd-log/$rngString")
        neg("in.co.pricealert.apps2sd", DATA, "apps2sd-mount-script.log")
        pos("in.co.pricealert.apps2sd", DATA, "apps2sd-log/apps2sd-mount-script.log")

        confirm(create())
    }

    @Test fun `test logs filter`() = runTest {
        addDefaultNegatives()
        neg(testPkg, SDCARD, "someapp/Logs")
        neg(testPkg, SDCARD, "someapp/logs")
        neg(testPkg, SDCARD, "someapp/logfiles")
        neg(testPkg, SDCARD, "someapp/Logfiles")
        neg(testPkg, SDCARD, "someapp/Log")
        neg(testPkg, SDCARD, "someapp/log")
        neg(testPkg, PUBLIC_DATA, "someapp/files/Logs")
        neg(testPkg, PUBLIC_DATA, "someapp/files/logs")
        neg(testPkg, PUBLIC_DATA, "someapp/files/logfiles")
        neg(testPkg, PUBLIC_DATA, "someapp/files/Logfiles")
        neg(testPkg, PUBLIC_DATA, "someapp/files/Log")
        neg(testPkg, PUBLIC_DATA, "someapp/files/log")
        neg(testPkg, SDCARD, "someapp/files/Logs/file")
        neg(testPkg, SDCARD, "someapp/files/logs/file")
        neg(testPkg, SDCARD, "someapp/files/logfiles/file")
        neg(testPkg, SDCARD, "someapp/files/Logfiles/file")
        neg(testPkg, SDCARD, "someapp/files/Log/file")
        neg(testPkg, SDCARD, "someapp/files/log/file")
        pos(testPkg, SDCARD, "someapp/Logs/$rngString")
        pos(testPkg, SDCARD, "someapp/logs/$rngString")
        pos(testPkg, SDCARD, "someapp/logfiles/$rngString")
        pos(testPkg, SDCARD, "someapp/Logfiles/$rngString")
        pos(testPkg, SDCARD, "someapp/Log/$rngString")
        pos(testPkg, SDCARD, "someapp/log/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/Logs/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/logs/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/logfiles/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/Logfiles/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/Log/$rngString")
        pos(testPkg, PUBLIC_DATA, "someapp/files/log/$rngString")

        confirm(create())
    }

    @Test fun `test male workout`() = runTest {
        addDefaultNegatives()
        neg("homeworkout.homeworkouts.noequipment", SDCARD, "MaleWorkout/crash")
        pos("homeworkout.homeworkouts.noequipment", SDCARD, "MaleWorkout/crash/$rngString")

        confirm(create())
    }

    @Test fun `test kingsoft office`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf(
            "cn.wps.moffice_i18n",
            "cn.wps.moffice_i18n_hd",
            "cn.wps.moffice",
            "cn.wps.moffice_eng"
        )
        neg(pkgs[0], PUBLIC_DATA, "cn.wps.moffice_eng/.cache/KingsoftOffice")
        neg(pkgs[0], PUBLIC_DATA, "cn.wps.moffice_eng/.cache/KingsoftOffice/$rngString")
        pos(pkgs[0], PUBLIC_DATA, "cn.wps.moffice_eng/.cache/KingsoftOffice/log/$rngString")
        pos(pkgs[0], PUBLIC_DATA, "cn.wps.moffice_eng/.cache/KingsoftOffice/.temp/$rngString")
        pos(pkgs[0], PUBLIC_DATA, "cn.wps.moffice/.cache/KingsoftOffice/log/$rngString")
        pos(pkgs[0], PUBLIC_DATA, "cn.wps.moffice/.cache/KingsoftOffice/.temp/$rngString")

        confirm(create())
    }

    @Test fun `test archos debug`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf(
            "com.archos.mediacenter.videofree",
            "com.archos.mediacenter.video"
        )
        neg(pkgs[0], SDCARD, "archos_debug")
        pos(pkgs[0], SDCARD, "archos_debug/$rngString")

        confirm(create())
    }

    @Test fun `test tencent micromsg`() = runTest {
        addDefaultNegatives()
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/sns/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/image2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/video/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/voice2/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/crash")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/crash/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/xlog")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/xlog/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/locallog")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/locallog/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/watchdog")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/watchdog/$rngString")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/failmsgfilecache")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/failmsgfilecache/$rngString")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/FTS5IndexMicroMsgInfo.txt")
        neg("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/logcat")
        pos("com.tencent.mm", SDCARD, "tencent/MicroMsg/efda91e6a3cd8c46008e42a3d3d614a3/logcat/$rngString")

        confirm(create())
    }

    @Test fun `test airdroid`() = runTest {
        addDefaultNegatives()
        neg("com.sand.airdroid", SDCARD, "AirDroid")
        pos("com.sand.airdroid", SDCARD, "AirDroid/exception.log")

        confirm(create())
    }

    @Test fun `test tencent encrypted logs`() = runTest {
        addDefaultNegatives()
        neg("some.pkg", SDCARD, "tencent/wns/EncryptLogs/some.pkg")
        pos("some.pkg", SDCARD, "tencent/wns/EncryptLogs/some.pkg/11-1111")
        pos("some.pkg", SDCARD, "tencent/wns/EncryptLogs/some.pkg/11-1111/1.wns.log")

        confirm(create())
    }

    @Test fun `test tencent msflogs`() = runTest {
        addDefaultNegatives()
        neg("some.test.pkg", SDCARD, "tencent/msflogs")
        neg("some.test.pkg", SDCARD, "tencent/msflogs/some")
        neg("some.test.pkg", SDCARD, "tencent/msflogs/some/test")
        neg("some.test.pkg", SDCARD, "tencent/msflogs/some/test/pkg")
        pos("some.test.pkg", SDCARD, "tencent/msflogs/some/test/pkg/something")

        confirm(create())
    }

    @Test fun `test musicolet`() = runTest {
        addDefaultNegatives()
        neg("in.krosbits.musicolet", SDCARD, "Musicolet")
        neg("in.krosbits.musicolet", SDCARD, "Musicolet/logs")
        neg("in.krosbits.musicolet", SDCARD, "Musicolet/logs/.nomedia")
        pos("in.krosbits.musicolet", SDCARD, "Musicolet/logs/last.crash")

        confirm(create())
    }

    @Test fun `test duplicates cleaner`() = runTest {
        addDefaultNegatives()
        neg("com.kaerosduplicatescleaner", SDCARD, "KaerosLogs")
        pos("com.kaerosduplicatescleaner", SDCARD, "KaerosLogs/something")

        confirm(create())
    }

    @Test fun `test gear log`() = runTest {
        addDefaultNegatives()
        val pkg = "com.samsung.android.app.watchmanager"
        neg(pkg, SDCARD, "log/GearLog")
        pos(pkg, SDCARD, "log/GearLog/dumpState-UHM.log")
        pos(pkg, SDCARD, "log/GearLog/dumpState_FOTA_PROVIDER_GEARO0.log")

        confirm(create())
    }

    @Test fun `test mozilla fenix`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("org.mozilla.fenix")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/gv_measurements")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/gv_measurements.json")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/gv_measurements-0.json")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/gv_measurements-1.json")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/gv_measurements-22.json")
        neg(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/glean_data")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/glean_data/pending_pings")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/glean_data/events")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/glean_data/events/events")
        pos(pkgs[0], PRIVATE_DATA, "org.mozilla.fenix/glean_data/glean_already_ran")

        confirm(create())
    }

    @Test fun `test tencent tbs`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.x.browser.x5", "com.tencent.something")
        neg(pkgs[0], SDCARD, "tbs")
        neg(pkgs[0], SDCARD, "tbs/.logTmp")
        pos(pkgs[0], SDCARD, "tbs/.logTmp/something")

        confirm(create())
    }

    @Test fun `test bixby`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.samsung.android.bixby.service")
        neg(pkgs[0], SDCARD, "log")
        neg(pkgs[0], SDCARD, "log/0_something")
        pos(pkgs[0], SDCARD, "log/0_com.samsung.android.bixby.service_bixbysearch_index.log.lck")
        pos(pkgs[0], SDCARD, "log/0_com.samsung.android.bixby.service_bixbysearch_index.log")
        pos(pkgs[0], SDCARD, "log/0_com.samsung.android.bixby.service/some other stuff")

        confirm(create())
    }

    @Test fun `test smart home`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.xiaomi.smarthome", "com.xiaomi.something")
        neg(pkgs[0], SDCARD, "wifi_config")
        pos(pkgs[0], SDCARD, "wifi_config.log")

        confirm(create())
    }

    @Test fun `test icbc wap log`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.icbc")
        neg(pkgs[0], SDCARD, "ICBCWAPLog")
        pos(pkgs[0], SDCARD, "ICBCWAPLog/junk.log")

        confirm(create())
    }

    @Test fun `test wd file hub`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("filehubplus.wd.activities")
        neg(pkgs[0], SDCARD, "FileHub Plus/log")
        pos(pkgs[0], SDCARD, "FileHub Plus/log/something")

        confirm(create())
    }

    @Test fun `test vmlog`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.vmos.glb")
        neg(pkgs[0], SDCARD, "vmlog/")
        pos(pkgs[0], SDCARD, "vmlog/something")
        neg(pkgs[0], SDCARD, "log.tx")
        neg(pkgs[0], SDCARD, "log.txt/test")
        pos(pkgs[0], SDCARD, "log.txt")

        confirm(create())
    }

    @Test fun `test microsoft feedback`() = runTest {
        addDefaultNegatives()
        val pkgs = arrayOf("com.microsoft.emmx")
        neg(pkgs[0], SDCARD, "Pictures")
        neg(pkgs[0], SDCARD, "Pictures/")
        pos(pkgs[0], SDCARD, "Pictures/Microsoft Edge feedback.jpg")
        pos(pkgs[0], SDCARD, "Pictures/Microsoft Edge feedback (1).jpg")

        confirm(create())
    }

    @Test fun `test luumi`() = runTest {
        addDefaultNegatives()
        neg("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.log")
        pos("photo.editor.photoeditor.filtersforpictures", SDCARD, "Lumii/.log/$rngString")

        confirm(create())
    }

    @Test fun `test body editor`() = runTest {
        addDefaultNegatives()
        neg("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.log")
        pos("breastenlarger.bodyeditor.photoeditor", SDCARD, "Body Editor/.log/something")

        confirm(create())
    }

    @Test fun `test meizu flyme`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.flyme.service.find", PUBLIC_DATA, "com.meizu.flyme.service.find/files")
        neg("com.meizu.flyme.service.find", PUBLIC_DATA, "com.meizu.flyme.service.find/files/log")
        pos("com.meizu.flyme.service.find", PUBLIC_DATA, "com.meizu.flyme.service.find/files/log.txt")

        confirm(create())
    }

    @Test fun `test meizu pps`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.pps", PUBLIC_DATA, "dcms")
        neg("com.meizu.pps", PUBLIC_DATA, "dcms/log")
        pos("com.meizu.pps", PUBLIC_DATA, "dcms/log/test")

        confirm(create())
    }

    @Test fun `test flyme sync log`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.mzsyncservice", SDCARD, "Android")
        neg("com.meizu.mzsyncservice", SDCARD, "Android/something")
        neg("com.meizu.mzsyncservice", SDCARD, "Android/flyme_sync_sdk_log.txt12")
        pos("com.meizu.mzsyncservice", SDCARD, "Android/flyme_sync_sdk_log.txt")
        pos("com.meizu.mzsyncservice", SDCARD, "Android/flyme_sync_sdk_log.txt/logs_v2.txt")
        pos("com.meizu.mzsyncservice", SDCARD, "Android/flyme_sync_sdk_log.txt/something")
        neg("com.meizu.mzsyncservice", PUBLIC_DATA, "com.meizu.mzsyncservice")
        pos("com.meizu.mzsyncservice", PUBLIC_DATA, "com.meizu.mzsyncservice/2020-09-19.log.txt")

        confirm(create())
    }

    @Test fun `test meizu customizer`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.customizecenter", SDCARD, "Customize/Log")
        pos("com.meizu.customizecenter", SDCARD, "Customize/Log/test")
        pos("com.meizu.customizecenter", SDCARD, "Customize/Log/1234567")

        confirm(create())
    }

    @Test fun `test android browser`() = runTest {
        addDefaultNegatives()
        neg("com.android.browser", PUBLIC_DATA, "com.android.browser")
        pos("com.android.browser", PUBLIC_DATA, "com.android.browser/gslb_sdk_log")
        pos("com.android.browser", PUBLIC_DATA, "com.android.browser/update_component_log")
        neg("com.android.browser", PUBLIC_DATA, "com.android.browser/files")
        pos("com.android.browser", PUBLIC_DATA, "com.android.browser/files/usage_logs_v2.txt")

        confirm(create())
    }

    @Test fun `test meizu toolbox`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.flyme.toolbox", PUBLIC_DATA, "com.meizu.flyme.toolbox")
        pos("com.meizu.flyme.toolbox", PUBLIC_DATA, "com.meizu.flyme.toolbox/update_component_log")

        confirm(create())
    }

    @Test fun `test music fx`() = runTest {
        addDefaultNegatives()
        neg("com.android.musicfx", PUBLIC_DATA, "com.android.musicfx")
        pos("com.android.musicfx", PUBLIC_DATA, "com.android.musicfx/gslb_log.txt")

        confirm(create())
    }

    @Test fun `test fly me service`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.flyme.service.find", PUBLIC_DATA, "com.meizu.flyme.service.find/files")
        pos("com.meizu.flyme.service.find", PUBLIC_DATA, "com.meizu.flyme.service.find/files/log.txt")

        confirm(create())
    }

    @Test fun `test fly me weather`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.flyme.weather", PUBLIC_DATA, "com.meizu.flyme.weather")
        pos("com.meizu.flyme.weather", PUBLIC_DATA, "com.meizu.flyme.weather/weather_log.txt")

        confirm(create())
    }

    @Test fun `test meizu account`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.account", PUBLIC_DATA, "com.meizu.account")
        pos("com.meizu.account", PUBLIC_DATA, "com.meizu.account/2020-09-19.log.txt")

        confirm(create())
    }

    @Test fun `test meizu input`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.flyme.input", PUBLIC_DATA, "com.meizu.flyme.input")
        pos("com.meizu.flyme.input", PUBLIC_DATA, "com.meizu.flyme.input/2020-09-19.log.txt")

        confirm(create())
    }

    @Test fun `test meizu video`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video")
        pos("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/update_component_plugin_log")
        pos("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/update_component_plugin_log/something")
        pos("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/update_component_log")
        pos("com.meizu.media.video", PUBLIC_DATA, "com.meizu.media.video/update_component_log/something")

        confirm(create())
    }

    @Test fun `test flyme corelog`() = runTest {
        addDefaultNegatives()

        val pkgs = arrayOf(
            "com.meizu.flyme.input",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi"
        )

        neg(pkgs[0], SDCARD, "sogou")
        neg(pkgs[0], SDCARD, "sogou/corelog")
        pos(pkgs[0], SDCARD, "sogou/corelog/activity_mini.txt")
        pos(pkgs[0], SDCARD, "sogou/corelog/activity.txt")

        confirm(create())
    }

    @Test fun `test video editor pro`() = runTest {
        addDefaultNegatives()
        neg("com.videoeditorpro.android", SDCARD, "logger/logs_")
        pos("com.videoeditorpro.android", SDCARD, "logger/logs_1.csv")
        pos("com.videoeditorpro.android", SDCARD, "logger/logs_2.csv")

        confirm(create())
    }

    @Test fun `test fly me upgrade log`() = runTest {
        addDefaultNegatives()

        neg("com.meizu.flyme.update", PUBLIC_DATA, "com.meizu.flyme.update/app_upgrade_l")
        pos("com.meizu.flyme.update", SDCARD, "com.meizu.flyme.update/app_upgrade_log")

        confirm(create())
    }

    @Test fun `test moffice logs`() = runTest {
        addDefaultNegatives()

        neg("cn.wps.moffice_eng", PUBLIC_DATA, "cn.wps.moffice_eng/files/klog")
        pos("cn.wps.moffice_eng", PUBLIC_DATA, "cn.wps.moffice_eng/files/klog/1")

        confirm(create())
    }

    @Test fun `test mi push log`() = runTest {
        addDefaultNegatives()

        neg(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/MiPushLog")
        pos(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/MiPushLog/log.lock")
        pos(testPkg, PUBLIC_DATA, "eu.thedarken.sdm.test/files/MiPushLog/log1.txt")

        confirm(create())
    }

    @Test fun `test xmsf logs`() = runTest {
        addDefaultNegatives()

        neg("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump")
        neg("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump/something")
        pos("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump/xmsf.log")
        pos("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump/xmsf.log.")
        pos("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump/xmsf.log.1")
        pos("com.xiaomi.xmsf", PUBLIC_DATA, "com.xiaomi.xmsf/files/dump/abc.log.1")

        confirm(create())
    }

    @Test fun `test miui gallery vlog`() = runTest {
        addDefaultNegatives()

        neg("com.miui.gallery", PUBLIC_DATA, "com.miui.gallery/files/vlog")
        pos("com.miui.gallery", PUBLIC_DATA, "com.miui.gallery/files/vlog/files")

        confirm(create())
    }

    @Test fun `test miui logger ui`() = runTest {
        addDefaultNegatives()

        neg("com.debug.loggerui", SDCARD, "debuglogger")
        pos("com.debug.loggerui", SDCARD, "debuglogger/files")

        confirm(create())
    }

    @Test fun `test tencent xlog`() = runTest {
        addDefaultNegatives()

        neg("video.like.lite", PUBLIC_DATA, "video.like.lite/files/xlo")
        neg("video.like.lite", PUBLIC_DATA, "video.like.lite/files/xlog")
        neg("video.like.lite", PUBLIC_DATA, "video.like.lite/cache/xlo")
        neg("video.like.lite", PUBLIC_DATA, "video.like.lite/cache/xlog")
        pos("video.like.lite", PUBLIC_DATA, "video.like.lite/files/xlog/5381")

        confirm(create())
    }

    @Test fun `test meizu contacts db log`() = runTest {
        addDefaultNegatives()

        neg("android", SDCARD, "Android")
        neg("android", SDCARD, "Android/contacts_db_log.txt")
        neg("com.android.providers.contacts", SDCARD, "Android")
        pos("com.android.providers.contacts", SDCARD, "Android/contacts_db_log.txt")

        confirm(create())
    }

    @Test fun `test meizu PPS dumps`() = runTest {
        addDefaultNegatives()

        neg("com.meizu.pps", SDCARD, "PPS")
        neg("com.meizu.pps", SDCARD, "PPS/something")
        pos("com.meizu.pps", SDCARD, "PPS/2020-08-06.txt")

        confirm(create())
    }

    @Test fun `test pics art debug logs`() = runTest {
        addDefaultNegatives()

        neg("com.picsart.studio", SDCARD, "Download")
        neg("com.picsart.studio", SDCARD, "Download/file")
        pos("com.picsart.studio", SDCARD, "Download/crash_log_1.txt")
        pos("com.picsart.studio", SDCARD, "Download/crash_log_2.txt")

        confirm(create())
    }

    @Test fun `test viber debug logs`() = runTest {
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
    @Test fun `test chinese logging library`() = runTest {
        addDefaultNegatives()

        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9")
        neg("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9/.nomedia")
        pos("com.lazada.android", PUBLIC_DATA, "com.lazada.android/files/tlog_v9/$rngString")

        confirm(create())
    }

    @Test fun `test dont match default caches`() = runTest {
        pos("some.pkg", PUBLIC_DATA, "some.pkg/files/log.txt")
        neg("some.pkg", PUBLIC_DATA, "some.pkg/cache/log.txt")
        neg("some.pkg", PUBLIC_DATA, "some.pkg/Cache/log.txt")

        pos("some.pkg", PRIVATE_DATA, "some.pkg/files/log.txt")
        neg("some.pkg", PRIVATE_DATA, "some.pkg/cache/log.txt")
        neg("some.pkg", PRIVATE_DATA, "some.pkg/Cache/log.txt")

        pos("some.pkg", SDCARD, "some.pkg/files/log.txt")
        pos("some.pkg", SDCARD, "some.pkg/cache/log.txt")
        pos("some.pkg", SDCARD, "some.pkg/Cache/log.txt")

        confirm(create())
    }

    @Test fun `test netease cloudmusic traces`() = runTest {
        addDefaultNegatives()

        neg("com.netease.cloudmusic", SDCARD, "netease/cloudmusic/Stacktrace")
        pos("com.netease.cloudmusic", SDCARD, "netease/cloudmusic/Stacktrace/deleteme")

        neg("com.netease.cloudmusic", PUBLIC_DATA, "com.netease.cloudmusic/files/Stacktrace")
        pos("com.netease.cloudmusic", PUBLIC_DATA, "com.netease.cloudmusic/files/Stacktrace/deleteme")

        confirm(create())
    }

    @Test fun `test tencent msflogs logs`() = runTest {
        addDefaultNegatives()

        neg("some.pkg", PUBLIC_DATA, "some.pkg/files/tencent/msflogs")
        pos(
            "some.pkg",
            PUBLIC_DATA,
            "some.pkg/files/tencent/msflogs/com/tencent/mobileqq/com.tencent.mobileqq_MSF.24.07.08.14.log"
        )
        neg("some.pkg", PRIVATE_DATA, "some.pkg/files/tencent/msflogs")
        pos(
            "some.pkg",
            PRIVATE_DATA,
            "some.pkg/files/tencent/msflogs/com/tencent/mobileqq/com.tencent.mobileqq_MSF.24.07.08.14.log"
        )

        confirm(create())
    }

    @Test fun `test qq chat crash logs`() = runTest {
        addDefaultNegatives()

        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/app_crashrecord")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/app_crashrecord/deleteme")
        neg("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/app_tombs")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/app_tombs/sys_log_1720437966614.txt")
        pos("com.tencent.mobileqq", PRIVATE_DATA, "com.tencent.mobileqq/app_tombs/jni_log_1720437966614.txt")

        confirm(create())
    }

    @Test fun `test tencent browser service log`() = runTest {
        addDefaultNegatives()

        neg("some.pkg", PRIVATE_DATA, "some.pkg/files/tbslog")
        pos("some.pkg", PRIVATE_DATA, "some.pkg/files/tbslog/deleteme.log")
        neg("some.pkg", PRIVATE_DATA, "some.pkg/files/Tencent/tbs_live_log")
        pos("some.pkg", PRIVATE_DATA, "some.pkg/files/Tencent/tbs_live_log/deleteme.log")

        neg("some.pkg", PUBLIC_DATA, "some.pkg/files/tbslog")
        pos("some.pkg", PUBLIC_DATA, "some.pkg/files/tbslog/deleteme.log")
        neg("some.pkg", PUBLIC_DATA, "some.pkg/files/Tencent/tbs_live_log")
        pos("some.pkg", PUBLIC_DATA, "some.pkg/files/Tencent/tbs_live_log/deleteme.log")

        confirm(create())
    }

    @Test
    fun `test crashlytics v2`() = runTest {
        addDefaultNegatives()


        neg(testPkg, PRIVATE_DATA, "$testPkg/files")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions")
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/report"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/start-time"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/aqs.1d48885e5465466aaff05984e33f3c64"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/native"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/native/session.json"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/native/app.json"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/native/os.json"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/native/device.json"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/keys"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/open-sessions/681C8CFB0050000152690FC22AD30334/userlog"
        )
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/reports")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/priority-reports")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/native-reports")
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.com.google.firebase.crashlytics.files.v2:$testPkg/com.crashlytics.settings.json"
        )

        confirm(create())
    }

    @Test fun `test crashlytics v3`() = runTest {
        addDefaultNegatives()

        neg(testPkg, PRIVATE_DATA, "some.pkg/files")
        neg(testPkg, PRIVATE_DATA, "some.pkg/files/.crashlytics.v3")
        neg(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions")
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/report"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/start-time"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/internal-keys"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/keys"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/userlog"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "$testPkg/files/.crashlytics.v3/$testPkg/open-sessions/ABCDEF10803280001217C85529785A6B1/aqs.22549f8ec9ce4bb59f66aa283eb3f0eb"
        )
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg/reports")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg/priority-reports")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg/native-reports")
        pos(testPkg, PRIVATE_DATA, "$testPkg/files/.crashlytics.v3/$testPkg/com.crashlytics.settings.json")

        confirm(create())
    }
}