package eu.darken.sdmse.systemcleaner.core.filter.sieve

import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class SegmentCriteriumTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi().newBuilder().apply {
        add(SegmentCriterium.MOSHI_ADAPTER_FACTORY)
    }.build()

    private val adapter = moshi.adapter(SegmentCriterium::class.java)

    @Test
    fun `ancestor serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = SegmentCriterium.Mode.Ancestor(ignoreCase = false)
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "segments": ["a", "b"],
                "mode": {
                    "type": "ANCESTOR",
                    "ignoreCase": false
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }

    @Test
    fun `start serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = SegmentCriterium.Mode.Start(allowPartial = true)
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "segments": ["a", "b"],
                "mode": {
                    "type": "START",
                    "ignoreCase": true,
                    "allowPartial": true
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original

        val defaultValuesRaw = """
           {
                "segments": ["a", "b"],
                "mode": {
                    "type": "START"
                }
            }
        """.toComparableJson()

        adapter.fromJson(defaultValuesRaw) shouldBe SegmentCriterium(
            segments = segs("a", "b"),
            mode = SegmentCriterium.Mode.Start(ignoreCase = true, allowPartial = false)
        )
    }

    @Test
    fun `contain serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = SegmentCriterium.Mode.Contain()
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "segments": ["a", "b"],
                "mode": {
                    "type": "CONTAIN",
                    "ignoreCase": true,
                    "allowPartial": false
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }

    @Test
    fun `end serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = SegmentCriterium.Mode.End()
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "segments": ["a", "b"],
                "mode": {
                    "type": "END",
                    "ignoreCase": true,
                    "allowPartial": false
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }
}