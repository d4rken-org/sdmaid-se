package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import eu.darken.sdmse.common.ui.LayoutMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class SqueezerSettingsTest : BaseTest() {

    private lateinit var json: Json

    @BeforeEach
    fun setup() {
        json = SerializationIOModule().json()
    }

    @Test
    fun `default MIN_FILE_SIZE is 512KB`() {
        SqueezerSettings.MIN_FILE_SIZE shouldBe 512 * 1024L
    }

    @Test
    fun `default quality is 80`() {
        SqueezerSettings.DEFAULT_QUALITY shouldBe 80
    }

    @Test
    fun `ScanPaths empty serialize matches golden JSON`() {
        val original = SqueezerSettings.ScanPaths(paths = emptySet())
        val jsonStr = json.encodeToString(SqueezerSettings.ScanPaths.serializer(), original)

        jsonStr.toComparableJson() shouldBe """
            {
                "paths": []
            }
        """.toComparableJson()

        json.decodeFromString(SqueezerSettings.ScanPaths.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `ScanPaths with paths serialize matches golden JSON`() {
        val original = SqueezerSettings.ScanPaths(
            paths = setOf(
                LocalPath.build("/storage/emulated/0/DCIM"),
                LocalPath.build("/storage/emulated/0/Pictures"),
            )
        )
        val jsonStr = json.encodeToString(SqueezerSettings.ScanPaths.serializer(), original)

        jsonStr.toComparableJson() shouldBe """
            {
                "paths": [
                    {
                        "file": "/storage/emulated/0/DCIM"
                    },
                    {
                        "file": "/storage/emulated/0/Pictures"
                    }
                ]
            }
        """.toComparableJson()

        val restored = json.decodeFromString(SqueezerSettings.ScanPaths.serializer(), jsonStr)
        restored shouldNotBe null
        restored.paths.size shouldBe 2
    }

    @Test
    fun `ScanPaths default constructor has empty paths`() {
        val scanPaths = SqueezerSettings.ScanPaths()
        scanPaths.paths shouldBe emptySet()
    }

    @Test
    fun `LayoutMode GRID serialize matches golden JSON`() {
        val jsonStr = json.encodeToString(LayoutMode.serializer(), LayoutMode.GRID)
        jsonStr shouldBe "\"GRID\""
    }

    @Test
    fun `LayoutMode LINEAR serialize matches golden JSON`() {
        val jsonStr = json.encodeToString(LayoutMode.serializer(), LayoutMode.LINEAR)
        jsonStr shouldBe "\"LINEAR\""
    }

    @Test
    fun `LayoutMode serializes and deserializes correctly`() {
        for (mode in LayoutMode.entries) {
            val jsonStr = json.encodeToString(LayoutMode.serializer(), mode)
            val restored = json.decodeFromString(LayoutMode.serializer(), jsonStr)
            restored shouldBe mode
        }
    }
}
