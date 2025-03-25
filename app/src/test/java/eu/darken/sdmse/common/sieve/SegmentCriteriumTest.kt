package eu.darken.sdmse.common.sieve

import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.common.sieve.SegmentCriterium.Mode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class SegmentCriteriumTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi().newBuilder().apply {
        add(SegmentCriterium.MOSHI_ADAPTER_FACTORY)
    }.build()

    private val adapter = moshi.adapter(SegmentCriterium::class.java)

    @Test
    fun `pfp criteria ANCESTOR - basic`() = runTest {
        SegmentCriterium("def", mode = Mode.Ancestor()).matchRaw("abc/def") shouldBe false
        SegmentCriterium("abc", mode = Mode.Ancestor()).matchRaw("abc/def") shouldBe true
        SegmentCriterium("abc/def", mode = Mode.Ancestor()).matchRaw("abc/def") shouldBe false
    }

    @Test
    fun `pfp criteria ANCESTOR - casing`() = runTest {
        SegmentCriterium("abc", mode = Mode.Ancestor(ignoreCase = false)).matchRaw("ABC/def") shouldBe false
        SegmentCriterium("abc", mode = Mode.Ancestor(ignoreCase = true)).matchRaw("ABC/def") shouldBe true
    }

    @Test
    fun `pfp criteria START - basic`() = runTest {
        SegmentCriterium("def", mode = Mode.Start()).matchRaw("abc/def") shouldBe false
        SegmentCriterium("abc", mode = Mode.Start()).matchRaw("abc/def") shouldBe true
    }

    @Test
    fun `pfp criteria START - partial`() = runTest {
        SegmentCriterium("ab", mode = Mode.Start(allowPartial = false)).matchRaw("abc/def") shouldBe false
        SegmentCriterium("ab", mode = Mode.Start(allowPartial = true)).matchRaw("abc/def") shouldBe true
        SegmentCriterium("abc/d", mode = Mode.Start(allowPartial = false)).matchRaw("abc/def") shouldBe false
        SegmentCriterium("abc/d", mode = Mode.Start(allowPartial = true)).matchRaw("abc/def") shouldBe true
    }

    @Test
    fun `pfp criteria START - casing`() = runTest {
        SegmentCriterium("abc", mode = Mode.Start(ignoreCase = false)).matchRaw("ABC/def") shouldBe false
        SegmentCriterium("abc", mode = Mode.Start(ignoreCase = true)).matchRaw("ABC/def") shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - basic`() = runTest {
        SegmentCriterium("123", mode = Mode.Contain()).matchRaw("abc/def") shouldBe false
        SegmentCriterium("abc", mode = Mode.Contain()).matchRaw("abc/def") shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - partial`() = runTest {
        SegmentCriterium("bc/de", mode = Mode.Contain(allowPartial = false)).matchRaw("abc/def") shouldBe false
        SegmentCriterium("bc/de", mode = Mode.Contain(allowPartial = true)).matchRaw("abc/def") shouldBe true
    }

    @Test
    fun `pfp criteria CONTAIN - casing`() = runTest {
        SegmentCriterium("def", mode = Mode.Contain(ignoreCase = false)).matchRaw("abc/DEF/ghi") shouldBe false
        SegmentCriterium("def", mode = Mode.Contain(ignoreCase = true)).matchRaw("abc/DEF/ghi") shouldBe true
    }

    @Test
    fun `pfp criteria END - basic`() = runTest {
        SegmentCriterium("abc/def", mode = Mode.End()).matchRaw("abc/def/ghi") shouldBe false
        SegmentCriterium("def/ghi", mode = Mode.End()).matchRaw("abc/def/ghi") shouldBe true
    }

    @Test
    fun `pfp criteria END - partial`() = runTest {
        SegmentCriterium("ef/ghi", mode = Mode.End(allowPartial = false)).matchRaw("abc/def/ghi") shouldBe false
        SegmentCriterium("ef/ghi", mode = Mode.End(allowPartial = true)).matchRaw("abc/def/ghi") shouldBe true
    }

    @Test
    fun `pfp criteria END - casing`() = runTest {
        SegmentCriterium("def/ghi", mode = Mode.End(ignoreCase = false)).matchRaw("abc/DEF/ghi") shouldBe false
        SegmentCriterium("def/ghi", mode = Mode.End(ignoreCase = true)).matchRaw("abc/DEF/ghi") shouldBe true
    }

    @Test
    fun `pfp criteria MATCH - basic`() = runTest {
        SegmentCriterium("abc/def/", mode = Mode.Equal()).matchRaw("abc/def") shouldBe false
        SegmentCriterium("abc/def", mode = Mode.Equal()).matchRaw("abc/def") shouldBe true
    }

    @Test
    fun `pfp criteria MATCH - casing`() = runTest {
        SegmentCriterium("abc/def", mode = Mode.Equal(ignoreCase = false)).matchRaw("abc/DEF") shouldBe false
        SegmentCriterium("abc/def", mode = Mode.Equal(ignoreCase = true)).matchRaw("abc/DEF") shouldBe true
    }

    @Test
    fun `ancestor serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = Mode.Ancestor(ignoreCase = false)
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
            mode = Mode.Start(allowPartial = true)
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
            mode = Mode.Start(ignoreCase = true, allowPartial = false)
        )
    }

    @Test
    fun `contain serialization`() {
        val original = SegmentCriterium(
            segments = segs("a", "b"),
            mode = Mode.Contain()
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
            mode = Mode.End()
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