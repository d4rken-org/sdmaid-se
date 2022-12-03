package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TencentTbsBackupDynamicMarkerTest {

    private val markerSource = TencentTbsBackupDynamicMarker()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, BASEDIR).size shouldBe 0
        markerSource.match(SDCARD, "${BASEDIR}/com.package.rollkuchen").single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun testBadMatches() = runTest {
        markerSource.match(SDCARD, "${BASEDIR}/.nomedia").size shouldBe 0
    }

    @Test fun testGetForLocation() = runTest {
        for (location in DataArea.Type.values()) {
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
        val testPkg = "com.pkg.test".toPkgId()
        markerSource.getMarkerForPkg(testPkg).single().apply {
            prefixFreeBasePath shouldBe "${BASEDIR}/$testPkg"
            match(SDCARD, "${BASEDIR}/$testPkg/something") shouldBe null
            match(SDCARD, "${BASEDIR}/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }
    }

    companion object {
        private const val BASEDIR = "tencent/tbs/backup"
    }
}