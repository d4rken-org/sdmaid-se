package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ExclusionImporterTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi()


    fun create() = ExclusionImporter(
        moshi = moshi
    )

    @Test
    fun `invalid data returns null`() = runTest {
        val importer = create()

        shouldThrow<IllegalArgumentException> {
            importer.import("") shouldBe null
        }
        shouldThrow<IllegalArgumentException> {
            importer.import("{\"exclusionRaw\": [], \"version\": 1}") shouldBe null
        }
    }

    @Test
    fun `empty data returns empty`() = runTest {
        val importer = create()

        importer.import("{\"exclusionRaw\": \"[]\", \"version\": 1}") shouldBe emptySet()
    }

    @Test
    fun `mixed exclusions`() = runTest {
        val importer = create()
        val og = setOf(
            PkgExclusion(
                pkgId = "test.pkg".toPkgId(),
                tags = setOf(Exclusion.Tag.GENERAL),
            ),
            SegmentExclusion(
                segments = "/test/path".toSegs(),
                tags = setOf(Exclusion.Tag.APPCLEANER),
                allowPartial = true,
                ignoreCase = true,
            ),
            PathExclusion(
                path = LocalPath.build("test", "path"),
                tags = setOf(Exclusion.Tag.APPCLEANER)
            ),
        )
        val raw = importer.export(og)

        raw.toComparableJson() shouldBe """
            {
                "exclusionRaw": "[{\"pkgId\":{\"name\":\"test.pkg\"},\"tags\":[\"GENERAL\"]},{\"segments\":[\"\",\"test\",\"path\"],\"allowPartial\":true,\"ignoreCase\":true,\"tags\":[\"APPCLEANER\"]},{\"path\":{\"file\":\"/test/path\",\"pathType\":\"LOCAL\"},\"tags\":[\"APPCLEANER\"]}]",
                "version": 1.0
            }
        """.toComparableJson()

        importer.import(raw) shouldBe og
    }
}