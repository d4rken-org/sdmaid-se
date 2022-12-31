package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EveryPlayDynamicMarkerTest {

    private val markerSource = EveryplayMarkerMatcher()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, listOf(".EveryplayCache")).size shouldBe 0

        markerSource.match(SDCARD, listOf(".EveryplayCache", "com.package.rollkuchen")).single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun testBadMatches() = runTest {
        markerSource.match(SDCARD, listOf(".EveryplayCache", ".nomedia")).size shouldBe 0

        markerSource.match(SDCARD, listOf(".EveryplayCache", "images")).size shouldBe 0

        markerSource.match(SDCARD, listOf(".EveryplayCache", "videos")).size shouldBe 0
    }

    @Test fun testGetForLocation() = runTest {
        for (location in DataArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    segments shouldBe EVERYPLAY_CACHE
                    isDirectMatch shouldBe false
                }
            } else {
                markers.isEmpty() shouldBe true
            }
        }
    }

    @Test fun testGetForPackageName() = runTest {
        val testPkg = "com.pkg.test"
        markerSource.getMarkerForPkg(testPkg.toPkgId()).single().apply {
            segments shouldBe EVERYPLAY_CACHE + listOf(testPkg)
            match(SDCARD, EVERYPLAY_CACHE + listOf(testPkg, "something")) shouldBe null
            match(SDCARD, EVERYPLAY_CACHE + listOf(testPkg)) shouldNotBe null
            isDirectMatch shouldBe true
        }
    }

    companion object {
        private val EVERYPLAY_CACHE = listOf(".EveryplayCache")
    }
}