package eu.darken.sdmse.exclusion.core

import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class LegacyImporterTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi()


    fun create() = LegacyImporter(
        moshi = moshi
    )

    @Test
    fun `invalid data returns null`() = runTest {
        val importer = create()

        shouldThrow<IllegalArgumentException> {
            importer.tryConvert("")
        }

        shouldThrow<IllegalArgumentException> {
            importer.tryConvert("{\"exclusions\": [], \"version\": 5}") shouldBe null
        }
    }

    @Test
    fun `empty data returns empty`() = runTest {
        val importer = create()

        importer.tryConvert("{\"exclusions\": [], \"version\": 6}") shouldBe emptySet()
    }

    @Test
    fun `regex is not yet supported data returns empty`() = runTest {
        val importer = create()

        importer.tryConvert(TEST_DATA_REGEX_PATH) shouldBe emptySet()
    }

    @Test
    fun `path to segment exclusions`() = runTest {
        val importer = create()

        importer.tryConvert(TEST_DATA_PATHS) shouldBe setOf(
            SegmentExclusion(
                segments = "/storage/emulated/0/DCIM".toSegs(),
                tags = setOf(Exclusion.Tag.CORPSEFINDER, Exclusion.Tag.SYSTEMCLEANER),
                allowPartial = true,
                ignoreCase = true,
            ),
            SegmentExclusion(
                segments = "/storage/emulated/0/Audiobooks".toSegs(),
                tags = setOf(Exclusion.Tag.GENERAL),
                allowPartial = true,
                ignoreCase = true,
            )
        )
    }

    @Test
    fun `pkg to pkg  exclusions`() = runTest {
        val importer = create()

        importer.tryConvert(TEST_DATA_PKGS) shouldBe setOf(
            PkgExclusion(
                pkgId = "eu.thedarken.sdm".toPkgId(),
                tags = setOf(Exclusion.Tag.GENERAL)
            ),
            PkgExclusion(
                pkgId = "android".toPkgId(),
                tags = setOf(Exclusion.Tag.GENERAL)
            )
        )
    }

    private val TEST_DATA_REGEX_PATH = """
        {
            "exclusions": [
                {
                    "regex_string": ".+Documents",
                    "tags": [
                        "DUPLICATES"
                    ],
                    "timestamp": 1702154638225,
                    "type": "REGEX"
                }
            ],
            "version": 6
        }
    """.trimIndent()

    private val TEST_DATA_PATHS = """
        {
            "exclusions": [
    
                {
                    "contains_string": "/storage/emulated/0/DCIM",
                    "tags": [
                        "CORPSEFINDER",
                        "SYSTEMCLEANER"
                    ],
                    "timestamp": 1702154578515,
                    "type": "SIMPLE_CONTAINS"
                },
                {
                    "contains_string": "/storage/emulated/0/Audiobooks",
                    "tags": [
                        "GLOBAL"
                    ],
                    "timestamp": 1702154570542,
                    "type": "SIMPLE_CONTAINS"
                }
            ],
            "version": 6
        }
    """.trimIndent()

    private val TEST_DATA_PKGS = """
        {
            "exclusions": [
                {
                    "contains_string": "eu.thedarken.sdm",
                    "tags": [
                        "APPCONTROL"
                    ],
                    "timestamp": 1702155430311,
                    "type": "SIMPLE_CONTAINS"
                },
                {
                    "contains_string": "android",
                    "tags": [
                        "GLOBAL"
                    ],
                    "timestamp": 1702155410652,
                    "type": "SIMPLE_CONTAINS"
                }
            ],
            "version": 6
        }
    """.trimIndent()
}