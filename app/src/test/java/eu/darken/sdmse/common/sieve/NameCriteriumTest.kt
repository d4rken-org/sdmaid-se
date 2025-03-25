package eu.darken.sdmse.common.sieve

import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.common.sieve.NameCriterium.Mode
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class NameCriteriumTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi().newBuilder().apply {
        add(NameCriterium.MOSHI_ADAPTER_FACTORY)
    }.build()

    private val adapter = moshi.adapter(NameCriterium::class.java)

    @Test
    fun `name criteria START - basic`() = runTest {
        NameCriterium("abc", mode = Mode.Start()).match("ghi") shouldBe false
        NameCriterium("ghi", mode = Mode.Start()).match("ghi") shouldBe true
    }

    @Test
    fun `name criteria START - casing`() = runTest {
        NameCriterium("ghi", mode = Mode.Start(ignoreCase = false)).match("GHI") shouldBe false
        NameCriterium("ghi", mode = Mode.Start(ignoreCase = true)).match("ghi") shouldBe true
    }

    @Test
    fun `name criteria CONTAIN - basic`() = runTest {
        NameCriterium("e", mode = Mode.Contain()).match("ghi") shouldBe false
        NameCriterium("h", mode = Mode.Contain()).match("ghi") shouldBe true
    }

    @Test
    fun `name criteria CONTAIN - casing`() = runTest {
        NameCriterium("h", mode = Mode.Contain(ignoreCase = false)).match("GHI") shouldBe false
        NameCriterium("h", mode = Mode.Contain(ignoreCase = true)).match("GHI") shouldBe true
    }

    @Test
    fun `name criteria END - basic`() = runTest {
        NameCriterium("h", mode = Mode.End()).match("ghi") shouldBe false
        NameCriterium("hi", mode = Mode.End()).match("ghi") shouldBe true
    }

    @Test
    fun `name criteria END - casing`() = runTest {
        NameCriterium("h", mode = Mode.End(ignoreCase = false)).match("gHI") shouldBe false
        NameCriterium("hi", mode = Mode.End(ignoreCase = true)).match("gHI") shouldBe true
    }

    @Test
    fun `name criteria EQUAL - basic`() = runTest {
        NameCriterium("def", mode = Mode.Equal()).match("ghi") shouldBe false
        NameCriterium("ghi", mode = Mode.Equal()).match("ghi") shouldBe true
    }

    @Test
    fun `name criteria EQUAL - casing`() = runTest {
        NameCriterium("ghi", mode = Mode.Equal(ignoreCase = false)).match("GHI") shouldBe false
        NameCriterium("ghi", mode = Mode.Equal(ignoreCase = true)).match("GHI") shouldBe true
    }


    @Test
    fun `match serialization`() {
        val original = NameCriterium(
            name = "some.apk",
            mode = Mode.Equal(ignoreCase = false)
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "name": "some.apk",
                "mode": {
                    "type": "MATCH",
                    "ignoreCase": false
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }

    @Test
    fun `start serialization`() {
        val original = NameCriterium(
            name = "some.apk",
            mode = Mode.Start()
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "name": "some.apk",
                "mode": {
                    "type": "START",
                    "ignoreCase": true
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }

    @Test
    fun `contain serialization`() {
        val original = NameCriterium(
            name = "some.apk",
            mode = Mode.Contain()
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "name": "some.apk",
                "mode": {
                    "type": "CONTAIN",
                    "ignoreCase": true
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }

    @Test
    fun `end serialization`() {
        val original = NameCriterium(
            name = "some.apk",
            mode = Mode.End()
        )
        val rawJson = adapter.toJson(original)
        rawJson.toComparableJson() shouldBe """
           {
                "name": "some.apk",
                "mode": {
                    "type": "END",
                    "ignoreCase": true
                }
            }
        """.toComparableJson()
        adapter.fromJson(rawJson) shouldBe original
    }
}