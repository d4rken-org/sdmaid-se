package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.StorageArea.Type.SDCARD
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PrivateToSdcardPathDevMistakeMarker2Test {

    private val markerSource = PrivateToSdcardPathDevMistakeMarker2()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, "data/user/0").size shouldBe 0
        markerSource.match(SDCARD, "data/user/0/com.package.rollkuchen").single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen"
        }
    }

    @Test fun testGetForLocation() = runTest {
        for (location in StorageArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    prefixFreeBasePath shouldBe "data/user/0"
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
            prefixFreeBasePath shouldBe "data/user/0/$testPkg"
            match(SDCARD, "data/user/0/$testPkg/something") shouldBe null
            match(SDCARD, "data/user/0/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }
    }
}