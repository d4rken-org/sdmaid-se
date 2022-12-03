package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.clutter.Marker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NestedPackageMatcherTest {
    @Test fun testBadInit_emptyPath() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageMatcher(SDCARD, "", emptySet())
        }
    }

    @Test fun testMatchingSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "dir", emptySet())
        markerSource.match(SDCARD, "dir").size shouldBe 0

        val matches = markerSource.match(SDCARD, "dir/com.package.rollkuchen").apply {
            size shouldBe 1
        }

        matches.iterator().next().packageNames.apply {
            size shouldBe 1
            first() shouldBe "com.package.rollkuchen"
        }
    }

    @Test fun testMatchingDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "double/nest", emptySet())

        markerSource.match(SDCARD, "double/nest").size shouldBe 0

        val matches = markerSource.match(SDCARD, "double/nest/com.package.rollkuchen").apply {
            size shouldBe 1
        }

        matches.iterator().next().packageNames.apply {
            size shouldBe 1
            first() shouldBe "com.package.rollkuchen"
        }
    }

    @Test fun testBadMatchesSingle() = runTest {
        NestedPackageMatcher(SDCARD, "dir", setOf("bad", "match")).apply {
            match(SDCARD, "dir/bad").size shouldBe 0
            match(SDCARD, "dir/match").size shouldBe 0
        }
    }

    @Test fun testBadMatchesDouble() = runTest {
        NestedPackageMatcher(SDCARD, "double/nest", setOf("bad", "match")).apply {
            match(SDCARD, "double/nest/bad").size shouldBe 0
            match(SDCARD, "double/nest/match").size shouldBe 0
            match(SDCARD, "double/bad").size shouldBe 0
            match(SDCARD, "double/match").size shouldBe 0
            match(SDCARD, "nest/bad").size shouldBe 0
            match(SDCARD, "nest/match").size shouldBe 0
            match(SDCARD, "double/nest").size shouldBe 0
            match(SDCARD, "double/nest").size shouldBe 0
        }
    }

    @Test fun testGetForLocationSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "dir", setOf("bad", "match"))
        for (location in DataArea.Type.values()) {
            val markers: Collection<Marker> = markerSource.getMarkerForLocation(location)

            if (location == SDCARD) {
                markers.size shouldBeGreaterThan 0
                markers.forEach { marker ->
                    marker.prefixFreeBasePath shouldBe "dir"
                    marker.isPrefixFreeBasePathDirect shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForLocationDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "double/nest", setOf("bad", "match"))
        for (location in DataArea.Type.values()) {
            val markers: Collection<Marker> = markerSource.getMarkerForLocation(location)

            if (location == SDCARD) {
                markers.size shouldBeGreaterThan 0
                markers.forEach { marker ->
                    marker.prefixFreeBasePath shouldBe "double/nest"
                    marker.isPrefixFreeBasePathDirect shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForPackageNameSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "dir", setOf("bad", "match"))
        val testPkg = "test.pkg"

        markerSource.getMarkerForPackageName(testPkg).single().apply {
            prefixFreeBasePath shouldBe "dir/$testPkg"

            match(SDCARD, "dir/$testPkg/something") shouldBe null
            match(SDCARD, "dir/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }
    }

    @Test fun testGetForPackageNameDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, "double/nest", setOf("bad", "match"))
        val testPkg = "test.pkg"

        markerSource.getMarkerForPackageName(testPkg).single().apply {
            prefixFreeBasePath shouldBe "double/nest/$testPkg"

            match(SDCARD, "double/nest/$testPkg/something") shouldBe null
            match(SDCARD, "double/nest/$testPkg") shouldNotBe null
            isPrefixFreeBasePathDirect shouldBe true
        }
    }
}