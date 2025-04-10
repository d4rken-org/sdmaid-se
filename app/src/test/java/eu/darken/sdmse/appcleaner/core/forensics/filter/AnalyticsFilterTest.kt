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

class AnalyticsFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AnalyticsFilter(
        jsonBasedSieveFactory = createJsonSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `fabric analytics filter`() = runTest {
        addDefaultNegatives()

        neg(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric/com.crashlytics.settings.json"
        )
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric/io.fabric.sdk.android:fabric")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send"
        )
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send/sa_4b97a040-f696-4b99-b7f8-1ac575da69b2_1457387170369.tap"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send/sa_289a994d-c636-4b6f-aa1b-8d0a1aa96c7b_1457876451033.tap"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics.tap"
        )

        confirm(create())
    }

    @Test fun `flurry analytics filter`() = runTest {
        addDefaultNegatives()

        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.flurry")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.yflurryreport.37062627c0fc131c")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.YFlurrySenderIndex.info.AnalyticsData_W7VDSPZRLLFUYKSU5DS5_195"
        )
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.YFlurrySenderIndex.info.AnalyticsMain")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.yflurrydatasenderblock.f65e2a60-e0cf-471c-966f-e623fe6c7baa"
        )
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.yflurrydatasenderblock.3b320f6f-f238-4025-8c6b-697da9347b7f"
        )
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/files/.flurryagent.2a92a562")
        pos(
            testPkg,
            PRIVATE_DATA,
            "eu.thedarken.sdm.test/files/.flurrydatasenderblock.3fc38c7e-65ae-4f38-9509-020db32aa4cf"
        )

        confirm(create())
    }

    @Test fun `adobe analytics filter`() = runTest {
        addDefaultNegatives()

        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/shared_prefs")
        neg(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/shared_prefs/APP_CACHE.xml")
        pos(testPkg, PRIVATE_DATA, "eu.thedarken.sdm.test/shared_prefs/APP_MEASUREMENT_CACHE.xml")
        
        confirm(create())
    }

    @Test fun `amazon market analytics filter`() = runTest {
        addDefaultNegatives()

        neg("com.amazon.mShop.android", PRIVATE_DATA, "com.amazon.mShop.android/app_venezia-metrics")
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_venezia-metrics/availability_measurements_1482267297904.measure"
        )
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_venezia-metrics/thecakeisalie_measurements_4444444444.measure"
        )
        neg("com.amazon.mShop.android", PRIVATE_DATA, "com.amazon.mShop.android/app_gamelab")
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_gamelab/availability_measurements_1482267313334.measure"
        )
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_gamelab/something_measurements_1111.measure"
        )
        neg("com.amazon.mShop.android", PRIVATE_DATA, "com.amazon.mShop.android/app_engagement")
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_engagement/availability_measurements_1482267758696.measure"
        )
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_engagement/something_measurements_2222222.measure"
        )
        neg("com.amazon.mShop.android", PRIVATE_DATA, "com.amazon.mShop.android/app_ad3-meta")
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_ad3-meta/availability_measurements_1482256138880.measure"
        )
        pos(
            "com.amazon.mShop.android",
            PRIVATE_DATA,
            "com.amazon.mShop.android/app_ad3-meta/strawberry_measurements_3333333333.measure"
        )

        confirm(create())
    }

    @Test fun `microsoft edge analytics filter`() = runTest {
        addDefaultNegatives()

        neg("com.microsoft.emmx", PUBLIC_DATA, "com.microsoft.emmx/files/")
        neg("com.microsoft.emmx", PUBLIC_DATA, "com.microsoft.emmx/files/$rngString")
        pos(
            "com.microsoft.emmx",
            PUBLIC_DATA,
            "com.microsoft.emmx/files/58be3288-43dc-4b90-902e-fa5a62eabada.norm.cllevent"
        )

        confirm(create())
    }

    @Test fun `microsoft cortana analytics filter`() = runTest {
        addDefaultNegatives()

        neg("com.microsoft.cortana", PUBLIC_DATA, "com.microsoft.cortana/files/")
        neg("com.microsoft.cortana", PUBLIC_DATA, "com.microsoft.cortana/files/$rngString")
        pos(
            "com.microsoft.cortana",
            PUBLIC_DATA,
            "com.microsoft.cortana/files/58be3288-43dc-4b90-902e-fa5a62eabada.norm.cllevent"
        )

        confirm(create())
    }

    @Test fun `tencent beacon analytics filter`() = runTest {
        addDefaultNegatives()

        neg("", SDCARD, "tencent/beacon")
        neg("", SDCARD, "tencent/beacon/.nomedia")
        pos("", SDCARD, "tencent/beacon/$rngString")
        
        confirm(create())
    }

    @Test fun `unity analytics filter`() = runTest {
        addDefaultNegatives()

        neg(
            "games.paveldogreat.fluidsim",
            PUBLIC_DATA,
            "games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics"
        )
        neg(
            "games.paveldogreat.fluidsim",
            PUBLIC_DATA,
            "games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9"
        )
        neg("games.paveldogreat.fluidsim", PUBLIC_DATA, "games.paveldogreat.fluidsim/files/Unity")
        neg("games.paveldogreat.fluidsim", PUBLIC_DATA, "games.paveldogreat.fluidsim/files")
        pos(
            "games.paveldogreat.fluidsim",
            PUBLIC_DATA,
            "games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents/157711799700099.c3c11664/g"
        )
        pos(
            "games.paveldogreat.fluidsim",
            PUBLIC_DATA,
            "games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents/157711799700099.c3c11664"
        )
        pos(
            "games.paveldogreat.fluidsim",
            PUBLIC_DATA,
            "games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents"
        )

        confirm(create())
    }

    @Test fun `pinterest sysdv analytics filter`() = runTest {
        addDefaultNegatives()

        neg("com.pinterest", SDCARD, ".sys")
        neg("com.pinterest", SDCARD, ".sys/$rngString")
        neg("com.pinterest", SDCARD, ".sysdv/")
        pos("com.pinterest", SDCARD, ".sysdv/$rngString")
        
        confirm(create())
    }

    @Test fun `netease cloudmusic analytics filter`() = runTest {
        addDefaultNegatives()

        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/MAMStatistic")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/MAMStatistic/deleteme")
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/MAMStatisticV2")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/MAMStatisticV2/deleteme")
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/RealTimeStatistic")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/RealTimeStatistic/deleteme")
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/Statistic")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/Statistic/deleteme")
        neg("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/StatisticV2")
        pos("com.netease.cloudmusic", PRIVATE_DATA, "com.netease.cloudmusic/files/StatisticV2/deleteme")

        confirm(create())
    }
}