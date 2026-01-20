package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class DeduplicatorSettingsSerializationTest : BaseTest() {
    private val moshi = SerializationAppModule().moshi()

    @Test
    fun `KeepPreferPaths serialization roundtrip`() {
        val original = DeduplicatorSettings.KeepPreferPaths(
            paths = setOf(
                LocalPath.build("storage", "emulated", "0", "Pictures"),
                LocalPath.build("storage", "emulated", "0", "DCIM"),
            )
        )

        val adapter = moshi.adapter(DeduplicatorSettings.KeepPreferPaths::class.java)
        val json = adapter.toJson(original)

        json.toComparableJson() shouldBe """
            {
                "paths": [
                    {"file": "/storage/emulated/0/Pictures", "pathType": "LOCAL"},
                    {"file": "/storage/emulated/0/DCIM", "pathType": "LOCAL"}
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `KeepPreferPaths empty paths`() {
        val original = DeduplicatorSettings.KeepPreferPaths()
        val adapter = moshi.adapter(DeduplicatorSettings.KeepPreferPaths::class.java)

        val json = adapter.toJson(original)
        adapter.fromJson(json) shouldBe original
    }
}
