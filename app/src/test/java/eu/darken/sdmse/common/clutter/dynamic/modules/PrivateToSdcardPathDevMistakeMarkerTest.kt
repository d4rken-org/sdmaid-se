package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrivateToSdcardPathDevMistakeMarkerTest {

    private val markerSource = PrivateToSdcardPathDevMistakeMarker()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, listOf("data", "data")).size shouldBe 0
        markerSource.match(SDCARD, listOf("data", "data", "com.package.rollkuchen")).single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun testGetForLocation() = runTest {
        for (location in DataArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    segments shouldBe listOf("data", "data")
                    isDirectMatch shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForPackageName() = runTest {
        val testPkg = "com.pkg.test"
        markerSource.getMarkerForPkg(testPkg.toPkgId()).single().apply {
            segments shouldBe listOf("data", "data", testPkg)
            match(SDCARD, listOf("data", "data", testPkg, "something")) shouldBe null
            match(SDCARD, listOf("data", "data", testPkg)) shouldNotBe null
            isDirectMatch shouldBe true
        }

    }
}