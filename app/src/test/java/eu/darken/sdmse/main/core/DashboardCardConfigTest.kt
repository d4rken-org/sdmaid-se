package eu.darken.sdmse.main.core

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DashboardCardConfigTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi()

    @Test
    fun `DashboardCardType enum values serialize correctly`() {
        val adapter = moshi.adapter(DashboardCardType::class.java)
        DashboardCardType.entries.forEach { type ->
            val json = adapter.toJson(type)
            json shouldBe "\"${type.name}\""
            adapter.fromJson(json) shouldBe type
        }
    }

    @Test
    fun `DashboardCardConfig round trip with default cards`() {
        val adapter = moshi.adapter(DashboardCardConfig::class.java)
        val original = DashboardCardConfig()

        val json = adapter.toJson(original)
        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `DashboardCardConfig default cards contains all types visible`() {
        val defaultConfig = DashboardCardConfig()

        defaultConfig.cards.size shouldBe DashboardCardType.entries.size
        defaultConfig.cards.forEach { entry ->
            entry.isVisible shouldBe true
        }
        defaultConfig.cards.map { it.type } shouldBe DashboardCardType.entries
    }

    @Test
    fun `DashboardCardConfig serialization format`() {
        val adapter = moshi.adapter(DashboardCardConfig::class.java)
        val config = DashboardCardConfig(
            cards = listOf(
                DashboardCardConfig.CardEntry(DashboardCardType.CORPSEFINDER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.APPCLEANER, isVisible = false),
            )
        )

        val json = adapter.toJson(config)
        json.toComparableJson() shouldBe """
            {
                "cards": [
                    {"type": "CORPSEFINDER", "isVisible": true},
                    {"type": "APPCLEANER", "isVisible": false}
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe config
    }

    @Test
    fun `DashboardCardConfig empty cards list`() {
        val adapter = moshi.adapter(DashboardCardConfig::class.java)
        val config = DashboardCardConfig(cards = emptyList())

        val json = adapter.toJson(config)
        json.toComparableJson() shouldBe """{"cards":[]}""".toComparableJson()

        adapter.fromJson(json) shouldBe config
    }

    @Test
    fun `DashboardCardConfig card order is preserved`() {
        val adapter = moshi.adapter(DashboardCardConfig::class.java)
        val reorderedCards = listOf(
            DashboardCardConfig.CardEntry(DashboardCardType.SCHEDULER),
            DashboardCardConfig.CardEntry(DashboardCardType.ANALYZER),
            DashboardCardConfig.CardEntry(DashboardCardType.CORPSEFINDER),
        )
        val config = DashboardCardConfig(cards = reorderedCards)

        val json = adapter.toJson(config)
        val deserialized = adapter.fromJson(json)!!

        deserialized.cards.map { it.type } shouldBe listOf(
            DashboardCardType.SCHEDULER,
            DashboardCardType.ANALYZER,
            DashboardCardType.CORPSEFINDER,
        )
    }

    @Test
    fun `CardEntry with isVisible false`() {
        val adapter = moshi.adapter(DashboardCardConfig.CardEntry::class.java)
        val entry = DashboardCardConfig.CardEntry(
            type = DashboardCardType.DEDUPLICATOR,
            isVisible = false,
        )

        val json = adapter.toJson(entry)
        json.toComparableJson() shouldBe """
            {"type": "DEDUPLICATOR", "isVisible": false}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe entry
    }

    @Test
    fun `CardEntry isVisible defaults to true when missing`() {
        val adapter = moshi.adapter(DashboardCardConfig.CardEntry::class.java)
        val json = """{"type":"CORPSEFINDER"}"""

        val entry = adapter.fromJson(json)!!
        entry.type shouldBe DashboardCardType.CORPSEFINDER
        entry.isVisible shouldBe true
    }

    @Test
    fun `unknown DashboardCardType throws JsonDataException`() {
        val adapter = moshi.adapter(DashboardCardType::class.java)

        shouldThrow<JsonDataException> {
            adapter.fromJson("\"UNKNOWN_CARD\"")
        }
    }

    @Test
    fun `CardEntry with unknown type throws JsonDataException`() {
        val adapter = moshi.adapter(DashboardCardConfig.CardEntry::class.java)
        val json = """{"type":"FUTURE_CARD_TYPE","isVisible":true}"""

        shouldThrow<JsonDataException> {
            adapter.fromJson(json)
        }
    }

    @Test
    fun `DashboardCardConfig with malformed JSON throws exception`() {
        val adapter = moshi.adapter(DashboardCardConfig::class.java)
        val json = """{"cards": [{"type": invalid}]}"""

        shouldThrow<Exception> {
            adapter.fromJson(json)
        }
    }
}
