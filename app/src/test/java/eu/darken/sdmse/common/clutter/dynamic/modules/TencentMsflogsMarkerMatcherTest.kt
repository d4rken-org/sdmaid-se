package eu.darken.sdmse.common.clutter.dynamic.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TencentMsflogsMarkerMatcherTest {

    private val markerSource = TencentMsflogsMarkerMatcher()

    @Test fun testMatching() = runTest {
        markerSource.match(SDCARD, BASEDIR).size shouldBe 0
        markerSource.match(SDCARD, BASEDIR + listOf("com", "package", "rollkuchen")).single().apply {
            packageNames.single() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun `matching with differernt casing`() = runTest {
        markerSource.match(SDCARD, BASEDIR).size shouldBe 0
        markerSource.match(SDCARD, BASEDIR + listOf("com", "package", "ROLLKUCHEN")).single().apply {
            packageNames.single() shouldBe "com.package.ROLLKUCHEN".toPkgId()
        }
    }

    @Test fun testBadMatches() = runTest {
        markerSource.match(SDCARD, BASEDIR + listOf(".nomedia")).size shouldBe 0
    }

    @Test fun testGetForLocation() = runTest {
        for (location in DataArea.Type.values()) {
            val markers = markerSource.getMarkerForLocation(location)
            if (location == SDCARD) {
                markers.single().apply {
                    segments shouldBe BASEDIR
                    isDirectMatch shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForPackageName() = runTest {
        val testPkg = "com/pkg/test"
        markerSource.getMarkerForPkg(testPkg.toPkgId()).single().apply {
            segments shouldBe BASEDIR.plus(testPkg)
            match(SDCARD, BASEDIR + listOf(testPkg, "something")) shouldBe null
            match(SDCARD, BASEDIR.plus(testPkg)) shouldNotBe null
            isDirectMatch shouldBe true
        }
    }

    companion object {
        private val BASEDIR = listOf("tencent", "msflogs")
    }
}