package eu.darken.sdmse.deduplicator.core

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DeduplicatorSettingsSerializationTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi()

    @Test
    fun `ArbiterCriterium DuplicateType serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.DuplicateType(mode = ArbiterCriterium.DuplicateType.Mode.PREFER_PHASH)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"DUPLICATE_TYPE","mode":"PREFER_PHASH"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium MediaProvider serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.MediaProvider(mode = ArbiterCriterium.MediaProvider.Mode.PREFER_UNKNOWN)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"MEDIA_PROVIDER","mode":"PREFER_UNKNOWN"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Location serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_SECONDARY)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"LOCATION","mode":"PREFER_SECONDARY"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Nesting serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Nesting(mode = ArbiterCriterium.Nesting.Mode.PREFER_DEEPER)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"NESTING","mode":"PREFER_DEEPER"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Modified serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"MODIFIED","mode":"PREFER_NEWER"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Size serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_SMALLER)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"SIZE","mode":"PREFER_SMALLER"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium PreferredPath serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.PreferredPath(
            keepPreferPaths = setOf(
                LocalPath.build("storage", "emulated", "0", "Pictures"),
                LocalPath.build("storage", "emulated", "0", "DCIM"),
            )
        )

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "criteriumType": "PREFERRED_PATH",
                "keepPreferPaths": [
                    {"file": "/storage/emulated/0/Pictures", "pathType": "LOCAL"},
                    {"file": "/storage/emulated/0/DCIM", "pathType": "LOCAL"}
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium PreferredPath empty paths serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.PreferredPath()

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"PREFERRED_PATH","keepPreferPaths":[]}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterConfig serialization roundtrip`() {
        val original = DeduplicatorSettings.ArbiterConfig(
            criteria = listOf(
                ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_NEWER),
                ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY),
            )
        )
        val adapter = moshi.adapter(DeduplicatorSettings.ArbiterConfig::class.java)
        val json = adapter.toJson(original)

        json.toComparableJson() shouldBe """
            {
                "criteria": [
                    {"criteriumType": "MODIFIED", "mode": "PREFER_NEWER"},
                    {"criteriumType": "LOCATION", "mode": "PREFER_PRIMARY"}
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterConfig default config serialization`() {
        val original = DeduplicatorSettings.ArbiterConfig()
        val adapter = moshi.adapter(DeduplicatorSettings.ArbiterConfig::class.java)

        val json = adapter.toJson(original)

        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
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
        val adapter = moshi.adapter(DeduplicatorSettings.ArbiterConfig::class.java)

        val json = adapter.toJson(original)
        adapter.fromJson(json) shouldBe original
    }

    // Missing mode variant tests

    @Test
    fun `ArbiterCriterium DuplicateType PREFER_CHECKSUM serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.DuplicateType(mode = ArbiterCriterium.DuplicateType.Mode.PREFER_CHECKSUM)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"DUPLICATE_TYPE","mode":"PREFER_CHECKSUM"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium MediaProvider PREFER_INDEXED serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.MediaProvider(mode = ArbiterCriterium.MediaProvider.Mode.PREFER_INDEXED)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"MEDIA_PROVIDER","mode":"PREFER_INDEXED"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Location PREFER_PRIMARY serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Location(mode = ArbiterCriterium.Location.Mode.PREFER_PRIMARY)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"LOCATION","mode":"PREFER_PRIMARY"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Nesting PREFER_SHALLOW serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Nesting(mode = ArbiterCriterium.Nesting.Mode.PREFER_SHALLOW)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"NESTING","mode":"PREFER_SHALLOW"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Modified PREFER_OLDER serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Modified(mode = ArbiterCriterium.Modified.Mode.PREFER_OLDER)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"MODIFIED","mode":"PREFER_OLDER"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `ArbiterCriterium Size PREFER_LARGER serialization`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val original = ArbiterCriterium.Size(mode = ArbiterCriterium.Size.Mode.PREFER_LARGER)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteriumType":"SIZE","mode":"PREFER_LARGER"}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    // Error handling tests

    @Test
    fun `ArbiterCriterium unknown criteriumType throws JsonDataException`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val json = """{"criteriumType":"UNKNOWN_TYPE","mode":"PREFER_NEWER"}"""

        shouldThrow<JsonDataException> {
            adapter.fromJson(json)
        }
    }

    @Test
    fun `ArbiterCriterium invalid mode throws JsonDataException`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val json = """{"criteriumType":"MODIFIED","mode":"PREFER_INVALID"}"""

        shouldThrow<JsonDataException> {
            adapter.fromJson(json)
        }
    }

    @Test
    fun `ArbiterCriterium missing criteriumType throws JsonDataException`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val json = """{"mode":"PREFER_NEWER"}"""

        shouldThrow<JsonDataException> {
            adapter.fromJson(json)
        }
    }

    @Test
    fun `ArbiterCriterium malformed JSON throws exception`() {
        val adapter = moshi.adapter(ArbiterCriterium::class.java)
        val json = """{"criteriumType":"MODIFIED", mode: invalid}"""

        shouldThrow<Exception> {
            adapter.fromJson(json)
        }
    }

    // Edge case tests

    @Test
    fun `ArbiterConfig empty criteria list serialization`() {
        val original = DeduplicatorSettings.ArbiterConfig(criteria = emptyList())
        val adapter = moshi.adapter(DeduplicatorSettings.ArbiterConfig::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {"criteria":[]}
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }
}
