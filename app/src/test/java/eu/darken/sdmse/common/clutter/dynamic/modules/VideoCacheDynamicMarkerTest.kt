package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.StorageArea.Type.SDCARD
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VideoCacheDynamicMarkerTest {
    private val markerSource = VideoCacheMarkerMatcher()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, BASEDIR).size shouldBe 0
        markerSource.match(SDCARD, "${BASEDIR}/com.package.rollkuchen").single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen"
        }
    }

    @Test fun testGetForLocation() = runTest {
        for (location in StorageArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    prefixFreeBasePath shouldBe BASEDIR
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
            prefixFreeBasePath shouldBe "${BASEDIR}/$testPkg"
            match(SDCARD, "${BASEDIR}/$testPkg/something") shouldBe null
            match(SDCARD, "${BASEDIR}/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }
    }

    companion object {
        private const val BASEDIR = "VideoCache"
    }
}