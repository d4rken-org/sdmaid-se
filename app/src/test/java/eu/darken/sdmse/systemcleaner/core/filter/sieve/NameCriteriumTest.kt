package eu.darken.sdmse.systemcleaner.core.filter.sieve

import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class NameCriteriumTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi().newBuilder().apply {
        add(NameCriterium.MOSHI_ADAPTER_FACTORY)
    }.build()

    private val adapter = moshi.adapter(NameCriterium::class.java)

    @Test
    fun `match serialization`() {
        val original = NameCriterium(
            name = "some.apk",
            mode = NameCriterium.Mode.Equal(ignoreCase = false)
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
            mode = NameCriterium.Mode.Start()
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
            mode = NameCriterium.Mode.Contain()
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
            mode = NameCriterium.Mode.End()
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