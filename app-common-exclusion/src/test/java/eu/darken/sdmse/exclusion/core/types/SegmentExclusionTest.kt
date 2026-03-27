package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class SegmentExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val json = SerializationIOModule().json()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }


    @Test
    fun `custom tags`() {
        testFile.tryMkFile()
        val original = SegmentExclusion(
            segments = segs("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER),
            allowPartial = true,
            ignoreCase = true,
        )

        val jsonStr = json.encodeToString(SegmentExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "segments": [
                    "test",
                    "path"
                ],
                "allowPartial": true,
                "ignoreCase": true,
                "tags": [
                    "DEDUPLICATOR",
                    "APPCLEANER"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(SegmentExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `direct serialization`() {
        testFile.tryMkFile()
        val original = SegmentExclusion(
            segments = segs("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER),
            allowPartial = true,
            ignoreCase = true,
        )

        val jsonStr = json.encodeToString(SegmentExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "segments": [
                    "test",
                    "path"
                ],
                "allowPartial": true,
                "ignoreCase": true,
                "tags": [
                    "DEDUPLICATOR",
                    "APPCLEANER"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(SegmentExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `polymorph serialization`() {
        testFile.tryMkFile()
        val original = SegmentExclusion(
            segments = segs("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER),
            allowPartial = true,
            ignoreCase = true,
        )

        val jsonStr = json.encodeToString(ExclusionSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "segments": [
                    "test",
                    "path"
                ],
                "allowPartial": true,
                "ignoreCase": true,
                "tags": [
                    "DEDUPLICATOR",
                    "APPCLEANER"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(ExclusionSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `force typing`() {
        val original = SegmentExclusion(
            segments = segs("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER),
            allowPartial = true,
            ignoreCase = true,
        )

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(SegmentExclusion.serializer(), original)
            json.decodeFromString(PathExclusion.serializer(), jsonStr)
        }
    }
}
