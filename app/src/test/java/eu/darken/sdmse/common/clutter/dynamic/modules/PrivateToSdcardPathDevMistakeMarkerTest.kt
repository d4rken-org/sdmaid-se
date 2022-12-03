package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrivateToSdcardPathDevMistakeMarkerTest {

    private val markerSource = PrivateToSdcardPathDevMistakeMarker()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, "data/data").size shouldBe 0
        markerSource.match(SDCARD, "data/data/com.package.rollkuchen").single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen"
        }
    }

    @Test fun testGetForLocation() = runTest {
        for (location in DataArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    prefixFreeBasePath shouldBe "data/data"
                    isPrefixFreeBasePathDirect shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForPackageName() = runTest {
        val testPkg = "com.pkg.test"
        markerSource.getMarkerForPackageName(testPkg).single().apply {
            prefixFreeBasePath shouldBe "data/data/$testPkg"
            match(SDCARD, "data/data/$testPkg/something") shouldBe null
            match(SDCARD, "data/data/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }

    }
}