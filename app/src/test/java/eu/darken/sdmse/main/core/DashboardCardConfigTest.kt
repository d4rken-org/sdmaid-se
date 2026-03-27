package eu.darken.sdmse.main.core

import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DashboardCardConfigTest : BaseTest() {
    private val json: Json = SerializationAppModule().json()

    @Test
    fun `DashboardCardType enum values serialize correctly`() {
        DashboardCardType.entries.forEach { type ->
            val serialized = json.encodeToString(DashboardCardType.serializer(), type)
            serialized shouldBe "\"${type.name}\""
            json.decodeFromString(DashboardCardType.serializer(), serialized) shouldBe type
        }
    }

    @Test
    fun `DashboardCardConfig round trip with default cards`() {
        val original = DashboardCardConfig()
        val serialized = json.encodeToString(DashboardCardConfig.serializer(), original)
        json.decodeFromString(DashboardCardConfig.serializer(), serialized) shouldBe original
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
        val config = DashboardCardConfig(
            cards = listOf(
                DashboardCardConfig.CardEntry(DashboardCardType.CORPSEFINDER, isVisible = true),
                DashboardCardConfig.CardEntry(DashboardCardType.APPCLEANER, isVisible = false),
            )
        )

        val serialized = json.encodeToString(DashboardCardConfig.serializer(), config)
        serialized.toComparableJson() shouldBe """
            {
                "cards": [
                    {"type": "CORPSEFINDER", "isVisible": true},
                    {"type": "APPCLEANER", "isVisible": false}
                ]
            }
        """.toComparableJson()

        json.decodeFromString(DashboardCardConfig.serializer(), serialized) shouldBe config
    }

    @Test
    fun `DashboardCardConfig empty cards list`() {
        val config = DashboardCardConfig(cards = emptyList())
        val serialized = json.encodeToString(DashboardCardConfig.serializer(), config)
        serialized.toComparableJson() shouldBe """{"cards":[]}""".toComparableJson()
        json.decodeFromString(DashboardCardConfig.serializer(), serialized) shouldBe config
    }

    @Test
    fun `DashboardCardConfig card order is preserved`() {
        val reorderedCards = listOf(
            DashboardCardConfig.CardEntry(DashboardCardType.SCHEDULER),
            DashboardCardConfig.CardEntry(DashboardCardType.ANALYZER),
            DashboardCardConfig.CardEntry(DashboardCardType.CORPSEFINDER),
        )
        val config = DashboardCardConfig(cards = reorderedCards)

        val serialized = json.encodeToString(DashboardCardConfig.serializer(), config)
        val deserialized = json.decodeFromString(DashboardCardConfig.serializer(), serialized)

        deserialized.cards.map { it.type } shouldBe listOf(
            DashboardCardType.SCHEDULER,
            DashboardCardType.ANALYZER,
            DashboardCardType.CORPSEFINDER,
        )
    }

    @Test
    fun `CardEntry with isVisible false`() {
        val entry = DashboardCardConfig.CardEntry(
            type = DashboardCardType.DEDUPLICATOR,
            isVisible = false,
        )

        val serialized = json.encodeToString(DashboardCardConfig.CardEntry.serializer(), entry)
        serialized.toComparableJson() shouldBe """
            {"type": "DEDUPLICATOR", "isVisible": false}
        """.toComparableJson()

        json.decodeFromString(DashboardCardConfig.CardEntry.serializer(), serialized) shouldBe entry
    }

    @Test
    fun `CardEntry isVisible defaults to true when missing`() {
        val raw = """{"type":"CORPSEFINDER"}"""
        val entry = json.decodeFromString(DashboardCardConfig.CardEntry.serializer(), raw)
        entry.type shouldBe DashboardCardType.CORPSEFINDER
        entry.isVisible shouldBe true
    }

    @Test
    fun `unknown DashboardCardType throws SerializationException`() {
        shouldThrow<SerializationException> {
            json.decodeFromString(DashboardCardType.serializer(), "\"UNKNOWN_CARD\"")
        }
    }

    @Test
    fun `CardEntry with unknown type throws SerializationException`() {
        val raw = """{"type":"FUTURE_CARD_TYPE","isVisible":true}"""
        shouldThrow<SerializationException> {
            json.decodeFromString(DashboardCardConfig.CardEntry.serializer(), raw)
        }
    }

    @Test
    fun `DashboardCardConfig with malformed JSON throws exception`() {
        val raw = """{"cards": [{"type": invalid}]}"""
        shouldThrow<Exception> {
            json.decodeFromString(DashboardCardConfig.serializer(), raw)
        }
    }
}
