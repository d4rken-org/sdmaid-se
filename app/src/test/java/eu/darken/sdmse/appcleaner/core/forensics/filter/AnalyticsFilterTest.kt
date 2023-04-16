package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

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
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    @Test fun testAnalyticsFilterFabric() = runTest {
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
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send/sa_4b97a040-f696-4b99-b7f8-1ac575da69b2_1457387170369.tap")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics_to_send/sa_289a994d-c636-4b6f-aa1b-8d0a1aa96c7b_1457876451033.tap")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.Fabric/com.crashlytics.sdk.android:answers/session_analytics.tap")

        )
        confirm(create())
    }

    @Test fun testAnalyticsFilterFlurry() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files"))
        addCandidate(neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.flurry"))
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.yflurryreport.37062627c0fc131c")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.YFlurrySenderIndex.info.AnalyticsData_W7VDSPZRLLFUYKSU5DS5_195")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.YFlurrySenderIndex.info.AnalyticsMain")
        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.yflurrydatasenderblock.f65e2a60-e0cf-471c-966f-e623fe6c7baa")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.yflurrydatasenderblock.3b320f6f-f238-4025-8c6b-697da9347b7f")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.flurryagent.2a92a562")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/files/.flurrydatasenderblock.3fc38c7e-65ae-4f38-9509-020db32aa4cf")

        )
        confirm(create())
    }

    @Test fun testAnalyticsAdobeAnalytics() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/shared_prefs"))
        addCandidate(
            neg().pkgs(testPkg).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/shared_prefs/APP_CACHE.xml")

        )
        addCandidate(
            pos().pkgs(testPkg).locs(PRIVATE_DATA)
                .prefixFree("eu.thedarken.sdm.test/shared_prefs/APP_MEASUREMENT_CACHE.xml")
        )
        confirm(create())
    }

    @Test fun testAmazonMarket_analytics() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_venezia-metrics")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_venezia-metrics/availability_measurements_1482267297904.measure")

        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_venezia-metrics/thecakeisalie_measurements_4444444444.measure")

        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA).prefixFree("com.amazon.mShop.android/app_gamelab")

        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_gamelab/availability_measurements_1482267313334.measure")

        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_gamelab/something_measurements_1111.measure")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_engagement")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_engagement/availability_measurements_1482267758696.measure")

        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_engagement/something_measurements_2222222.measure")
        )
        addCandidate(
            neg().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_ad3-meta")
        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_ad3-meta/availability_measurements_1482256138880.measure")

        )
        addCandidate(
            pos().pkgs("com.amazon.mShop.android").locs(PRIVATE_DATA)
                .prefixFree("com.amazon.mShop.android/app_ad3-meta/strawberry_measurements_3333333333.measure")
        )
        confirm(create())
    }

    @Test fun testMSEdge() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.microsoft.emmx").locs(PUBLIC_DATA, PRIVATE_DATA).prefixFree("com.microsoft.emmx/files/")

        )
        addCandidate(
            neg().pkgs("com.microsoft.emmx").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.microsoft.emmx/files/" + UUID.randomUUID())
        )
        addCandidate(
            pos().pkgs("com.microsoft.emmx").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.microsoft.emmx/files/58be3288-43dc-4b90-902e-fa5a62eabada.norm.cllevent")
        )
        confirm(create())
    }

    @Test fun testCortana() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.microsoft.cortana").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.microsoft.cortana/files/")
        )
        addCandidate(
            neg().pkgs("com.microsoft.cortana").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.microsoft.cortana/files/" + rngString)
        )
        addCandidate(
            pos().pkgs("com.microsoft.cortana").locs(PUBLIC_DATA, PRIVATE_DATA)
                .prefixFree("com.microsoft.cortana/files/58be3288-43dc-4b90-902e-fa5a62eabada.norm.cllevent")
        )
        confirm(create())
    }

    @Test fun testTencentBeacon() = runTest {
        addDefaultNegatives()
        addCandidate(neg().locs(SDCARD).prefixFree("tencent/beacon"))
        addCandidate(neg().locs(SDCARD).prefixFree("tencent/beacon/.nomedia"))
        addCandidate(pos().locs(SDCARD).prefixFree("tencent/beacon/" + rngString))
        confirm(create())
    }

    @Test fun testUnityAnalytics() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics")
        )
        addCandidate(
            neg().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9")
        )
        addCandidate(
            neg().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity")
        )
        addCandidate(
            neg().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA).prefixFree("games.paveldogreat.fluidsim/files")
        )
        addCandidate(
            pos().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents/157711799700099.c3c11664/g")
        )
        addCandidate(
            pos().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents/157711799700099.c3c11664")
        )
        addCandidate(
            pos().pkgs("games.paveldogreat.fluidsim").locs(PUBLIC_DATA)
                .prefixFree("games.paveldogreat.fluidsim/files/Unity/29018317-00d5-499b-87d2-5177b9d551c9/Analytics/ArchivedEvents")
        )
        confirm(create())
    }


    @Test fun testSysDV() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.pinterest").locs(SDCARD).prefixFree(".sys"))
        addCandidate(neg().pkgs("com.pinterest").locs(SDCARD).prefixFree(".sys/$rngString"))
        addCandidate(neg().pkgs("com.pinterest").locs(SDCARD).prefixFree(".sysdv/"))
        addCandidate(pos().pkgs("com.pinterest").locs(SDCARD).prefixFree(".sysdv/$rngString"))
        confirm(create())
    }
}