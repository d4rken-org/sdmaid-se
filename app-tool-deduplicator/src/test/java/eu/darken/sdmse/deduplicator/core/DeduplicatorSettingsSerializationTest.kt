package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DeduplicatorSettingsSerializationTest : BaseTest() {
    private val json: Json = SerializationIOModule().json()

    @Test
    fun `ArbiterCriterium DuplicateType serialization`() {
        val original = ArbiterCriterium.DuplicateType(mode = ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"DUPLICATE_TYPE","mode":"PREFER_PHASH"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium MediaProvider serialization`() {
        val original = ArbiterCriterium.MediaProvider(mode = ArbiterCriterium.MediaProvider.Mode.PREFER_UNKNOWN)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"MEDIA_PROVIDER","mode":"PREFER_UNKNOWN"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Location serialization`() {
        val original = ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_SECONDARY)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"LOCATION","mode":"PREFER_SECONDARY"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Nesting serialization`() {
        val original = ArbiterCriterium.Nesting(mode = ArbiterCriterium.Nesting.Mode.PREFER_DEEPER)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"NESTING","mode":"PREFER_DEEPER"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Modified serialization`() {
        val original = ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"MODIFIED","mode":"PREFER_NEWER"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Size serialization`() {
        val original = ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_SMALLER)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"SIZE","mode":"PREFER_SMALLER"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium PreferredPath serialization`() {
        val original = ArbiterCriterium.PreferredPath(
            keepPreferPaths = setOf(
                LocalPath.build("storage", "emulated", "0", "Pictures"),
                LocalPath.build("storage", "emulated", "0", "DCIM"),
            )
        )

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "criteriumType": "PREFERRED_PATH",
                "keepPreferPaths": [
                    {"file": "/storage/emulated/0/Pictures"},
                    {"file": "/storage/emulated/0/DCIM"}
                ]
            }
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium PreferredPath empty paths serialization`() {
        val original = ArbiterCriterium.PreferredPath()

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"PREFERRED_PATH","keepPreferPaths":[]}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterConfig serialization roundtrip`() {
        val original = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER),
                ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
            )
        )
        val jsonStr = json.encodeToString(DeduplicatorSettings.ArbiterConfig.serializer(), original)

        jsonStr.toComparableJson() shouldBe """
            {
                "criteria": [
                    {"criteriumType": "MODIFIED", "mode": "PREFER_NEWER"},
                    {"criteriumType": "LOCATION", "mode": "PREFER_PRIMARY"}
                ]
            }
        """.toComparableJson()

        json.decodeFromString(DeduplicatorSettings.ArbiterConfig.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterConfig default config serialization`() {
        val original = DeduplicatorSettings.ArbiterConfig()

        val jsonStr = json.encodeToString(DeduplicatorSettings.ArbiterConfig.serializer(), original)

        jsonStr.toComparableJson() shouldBe """
            {
                "criteria": [
                    {"criteriumType": "DUPLICATE_TYPE", "mode": "PREFER_CHECKSUM"},
                    {"criteriumType": "PREFERRED_PATH", "keepPreferPaths": []},
                    {"criteriumType": "MEDIA_PROVIDER", "mode": "PREFER_INDEXED"},
                    {"criteriumType": "LOCATION", "mode": "PREFER_PRIMARY"},
                    {"criteriumType": "NESTING", "mode": "PREFER_SHALLOW"},
                    {"criteriumType": "MODIFIED", "mode": "PREFER_OLDER"},
                    {"criteriumType": "SIZE", "mode": "PREFER_LARGER"}
                ]
            }
        """.toComparableJson()

        json.decodeFromString(DeduplicatorSettings.ArbiterConfig.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterConfig with PreferredPath containing paths`() {
        val original = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.PreferredPath(
                    keepPreferPaths = setOf(
                        LocalPath.build("storage", "emulated", "0", "DCIM"),
                    )
                ),
                ArbiterCriterium.DuplicateType(),
            )
        )

        val jsonStr = json.encodeToString(DeduplicatorSettings.ArbiterConfig.serializer(), original)
        json.decodeFromString(DeduplicatorSettings.ArbiterConfig.serializer(), jsonStr) shouldBe original
    }

    // Missing mode variant tests

    @Test
    fun `ArbiterCriterium DuplicateType PREFER_CHECKSUM serialization`() {
        val original = ArbiterCriterium.DuplicateType(mode = ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"DUPLICATE_TYPE","mode":"PREFER_CHECKSUM"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium MediaProvider PREFER_INDEXED serialization`() {
        val original = ArbiterCriterium.MediaProvider(mode = ArbiterCriterium.MediaProvider.Mode.PREFER_INDEXED)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"MEDIA_PROVIDER","mode":"PREFER_INDEXED"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Location PREFER_PRIMARY serialization`() {
        val original = ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"LOCATION","mode":"PREFER_PRIMARY"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Nesting PREFER_SHALLOW serialization`() {
        val original = ArbiterCriterium.Nesting(mode = ArbiterCriterium.Nesting.Mode.PREFER_SHALLOW)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"NESTING","mode":"PREFER_SHALLOW"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Modified PREFER_OLDER serialization`() {
        val original = ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_OLDER)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"MODIFIED","mode":"PREFER_OLDER"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Size PREFER_LARGER serialization`() {
        val original = ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER)

        val jsonStr = json.encodeToString(ArbiterCriterium.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteriumType":"SIZE","mode":"PREFER_LARGER"}
        """.toComparableJson()

        json.decodeFromString(ArbiterCriterium.serializer(), jsonStr) shouldBe original
    }

    // Error handling tests

    @Test
    fun `ArbiterCriterium unknown criteriumType throws SerializationException`() {
        val jsonStr = """{"criteriumType":"UNKNOWN_TYPE","mode":"PREFER_NEWER"}"""

        shouldThrow<SerializationException> {
            json.decodeFromString(ArbiterCriterium.serializer(), jsonStr)
        }
    }

    @Test
    fun `ArbiterCriterium invalid mode throws SerializationException`() {
        val jsonStr = """{"criteriumType":"MODIFIED","mode":"PREFER_INVALID"}"""

        shouldThrow<SerializationException> {
            json.decodeFromString(ArbiterCriterium.serializer(), jsonStr)
        }
    }

    @Test
    fun `ArbiterCriterium missing criteriumType throws SerializationException`() {
        val jsonStr = """{"mode":"PREFER_NEWER"}"""

        shouldThrow<SerializationException> {
            json.decodeFromString(ArbiterCriterium.serializer(), jsonStr)
        }
    }

    @Test
    fun `ArbiterCriterium malformed JSON throws exception`() {
        val jsonStr = """{"criteriumType":"MODIFIED", mode: invalid}"""

        shouldThrow<Exception> {
            json.decodeFromString(ArbiterCriterium.serializer(), jsonStr)
        }
    }

    // Edge case tests

    @Test
    fun `ArbiterConfig empty criteria list serialization`() {
        val original = DeduplicatorSettings.ArbiterConfig(criteria = emptyList())

        val jsonStr = json.encodeToString(DeduplicatorSettings.ArbiterConfig.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {"criteria":[]}
        """.toComparableJson()

        json.decodeFromString(DeduplicatorSettings.ArbiterConfig.serializer(), jsonStr) shouldBe original
    }
}
