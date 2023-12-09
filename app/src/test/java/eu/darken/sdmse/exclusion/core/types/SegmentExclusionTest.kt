package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class SegmentExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val moshi = SerializationAppModule().moshi()

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

        val adapter = moshi.adapter(SegmentExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
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

        val adapter = moshi.adapter(SegmentExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
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

        val adapter = moshi.adapter(Exclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `force typing`() {
        val original = SegmentExclusion(
            segments = segs("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER),
            allowPartial = true,
            ignoreCase = true,
        )

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(SegmentExclusion::class.java).toJson(original)
            moshi.adapter(PathExclusion::class.java).fromJson(json)
        }
    }
}