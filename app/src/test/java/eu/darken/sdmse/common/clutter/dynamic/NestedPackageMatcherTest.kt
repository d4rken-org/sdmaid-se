package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NestedPackageMatcherTest {
    @Test fun testBadInit_emptyPath() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageMatcher(SDCARD, listOf(""), emptySet())
        }
    }

    @Test fun testMatchingSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("dir"), emptySet())
        markerSource.match(SDCARD, listOf("dir")).size shouldBe 0

        val matches = markerSource.match(SDCARD, listOf("dir", "com.package.rollkuchen")).apply {
            size shouldBe 1
        }

        matches.iterator().next().packageNames.apply {
            size shouldBe 1
            first() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun testMatchingDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("double", "nest"), emptySet())

        markerSource.match(SDCARD, listOf("double", "nest")).size shouldBe 0

        val matches = markerSource.match(SDCARD, listOf("double", "nest", "com.package.rollkuchen")).apply {
            size shouldBe 1
        }

        matches.iterator().next().packageNames.apply {
            size shouldBe 1
            first() shouldBe "com.package.rollkuchen".toPkgId()
        }
    }

    @Test fun testBadMatchesSingle() = runTest {
        NestedPackageMatcher(SDCARD, listOf("dir"), setOf("bad", "match")).apply {
            match(SDCARD, listOf("dir", "bad")).size shouldBe 0
            match(SDCARD, listOf("dir", "match")).size shouldBe 0
        }
    }

    @Test fun testBadMatchesDouble() = runTest {
        NestedPackageMatcher(SDCARD, listOf("double", "nest"), setOf("bad", "match")).apply {
            match(SDCARD, listOf("double", "nest", "bad")).size shouldBe 0
            match(SDCARD, listOf("double", "nest", "match")).size shouldBe 0
            match(SDCARD, listOf("double", "bad")).size shouldBe 0
            match(SDCARD, listOf("double", "match")).size shouldBe 0
            match(SDCARD, listOf("nest", "bad")).size shouldBe 0
            match(SDCARD, listOf("nest", "match")).size shouldBe 0
            match(SDCARD, listOf("double", "nest")).size shouldBe 0
            match(SDCARD, listOf("double", "nest")).size shouldBe 0
        }
    }

    @Test fun testGetForLocationSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("dir"), setOf("bad", "match"))
        for (location in DataArea.Type.values()) {
            val markers: Collection<Marker> = markerSource.getMarkerForLocation(location)

            if (location == SDCARD) {
                markers.size shouldBeGreaterThan 0
                markers.forEach { marker ->
                    marker.segments shouldBe listOf("dir")
                    marker.isDirectMatch shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForLocationDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("double", "nest"), setOf("bad", "match"))
        for (location in DataArea.Type.values()) {
            val markers: Collection<Marker> = markerSource.getMarkerForLocation(location)

            if (location == SDCARD) {
                markers.size shouldBeGreaterThan 0
                markers.forEach { marker ->
                    marker.segments shouldBe listOf("double", "nest")
                    marker.isDirectMatch shouldBe false
                }
            } else {
                markers.size shouldBe 0
            }
        }
    }

    @Test fun testGetForPackageNameSingle() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("dir"), setOf("bad", "match"))
        val testPkg = "test.pkg".toPkgId()

        markerSource.getMarkerForPkg(testPkg).single().apply {
            segments shouldBe listOf("dir", "$testPkg")

            match(SDCARD, listOf("dir", "$testPkg", "something")) shouldBe null
            match(SDCARD, listOf("dir", "$testPkg")) shouldNotBe null
            isDirectMatch shouldBe true
        }
    }

    @Test fun testGetForPackageNameDouble() = runTest {
        val markerSource = NestedPackageMatcher(SDCARD, listOf("double", "nest"), setOf("bad", "match"))
        val testPkg = "test.pkg".toPkgId()

        markerSource.getMarkerForPkg(testPkg).single().apply {
            segments shouldBe listOf("double", "nest", "$testPkg")

            match(SDCARD, listOf("double", "nest", "$testPkg", "something")) shouldBe null
            match(SDCARD, listOf("double", "nest", "$testPkg")) shouldNotBe null
            isDirectMatch shouldBe true
        }
    }
}