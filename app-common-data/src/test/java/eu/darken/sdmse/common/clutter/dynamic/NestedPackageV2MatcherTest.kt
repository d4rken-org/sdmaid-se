package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File

class NestedPackageV2MatcherTest {

    val stubConverter = object : NestedPackageV2Matcher.Converter {
        override fun onConvertMatchToPackageNames(match: MatchResult): Set<Pkg.Id> = emptySet()

        override fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>> = emptySet()
    }

    @Test fun testBadInit_empty1() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageV2Matcher(
                SDCARD,
                listOf(""),
                setOf(Regex("")),
                emptySet(),
                emptySet(),
                stubConverter,
            )
        }
    }

    @Test fun testBadInit_empty2() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageV2Matcher(
                SDCARD,
                listOf("prefix"),
                emptySet(),
                emptySet(),
                emptySet(),
                stubConverter,
            )
        }
    }

    @Test fun testBadInit_badPrefix1() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageV2Matcher(
                SDCARD,
                listOf("bad", "prefix"),
                setOf(Regex("")),
                emptySet(),
                emptySet(),
                stubConverter,
            )
        }
    }

    @Test fun testBadInit_badPrefix2() {
        shouldThrow<IllegalArgumentException> {
            NestedPackageV2Matcher(
                SDCARD,
                listOf("/"),
                setOf(Regex("")),
                emptySet(),
                emptySet(),
                stubConverter,
            )
        }
    }

    @Test fun testSimpleCase() = runTest {
        val converter: NestedPackageV2Matcher.Converter = object : NestedPackageV2Matcher.Converter {
            override fun onConvertMatchToPackageNames(match: MatchResult): Set<Pkg.Id> {
                val pkgs: MutableSet<Pkg.Id> = LinkedHashSet()
                pkgs.add(match.groupValues[1].replace(File.separatorChar, '.').toPkgId())
                return pkgs
            }

            override fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>> {
                val paths: MutableSet<List<String>> = LinkedHashSet()
                paths.add(pkgId.name.split('.'))
                return paths
            }
        }
        val markerSource = NestedPackageV2Matcher(
            SDCARD,
            listOf("pre", "fix"),
            setOf(Regex("^(?>pre/fix/((?:\\w+/){2}\\w+))$", RegexOption.IGNORE_CASE)),
            setOf(Regex("^(?>pre/fix/((?:\\w+/){2}bad_last_dir))$", RegexOption.IGNORE_CASE)),  // BAD
            setOf(Marker.Flag.COMMON, Marker.Flag.KEEPER, Marker.Flag.CUSTODIAN),
            converter
        )
        markerSource.match(SDCARD, listOf("pre")).size shouldBe 0
        markerSource.match(SDCARD, listOf("prefix")).size shouldBe 0

        run {

            // This should have no match
            markerSource.match(SDCARD, listOf("pre", "fix", "com", "package", "bad_last_dir")).size shouldBe 0
        }
        run {
            // Normal match
            markerSource.match(SDCARD, listOf("pre", "fix", "com", "package", "rollkuchen")).single().apply {
                packageNames.single() shouldBe "com.package.rollkuchen".toPkgId()
                flags shouldBe setOf(Marker.Flag.COMMON, Marker.Flag.KEEPER, Marker.Flag.CUSTODIAN)
            }
        }
        run {
            // Casing
            markerSource.match(SDCARD, listOf("pre", "fix", "com", "package", "ROLLKUCHEN")).single().apply {
                packageNames.single() shouldBe "com.package.ROLLKUCHEN".toPkgId()
                flags shouldBe setOf(Marker.Flag.COMMON, Marker.Flag.KEEPER, Marker.Flag.CUSTODIAN)
            }
        }
        run {
            // getMarkerForPackageName
            markerSource.getMarkerForPkg("com.package.ROLLKUCHEN".toPkgId()).single().apply {
                segments shouldBe listOf("pre", "fix", "com", "package", "ROLLKUCHEN")
                isDirectMatch shouldBe true
            }
        }
        run {
            // getMarkerForLocation
            markerSource.getMarkerForLocation(SDCARD).single().apply {
                segments shouldBe listOf("pre", "fix")
                isDirectMatch shouldBe false
            }
        }
    }
}