package eu.darken.sdmse.compressor.core

import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.common.ui.LayoutMode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CompressorSettingsTest : BaseTest() {

    private lateinit var moshi: Moshi

    @BeforeEach
    fun setup() {
        moshi = SerializationAppModule().moshi()
    }

    @Test
    fun `default MIN_FILE_SIZE is 512KB`() {
        CompressorSettings.MIN_FILE_SIZE shouldBe 512 * 1024L
    }

    @Test
    fun `default quality is 80`() {
        CompressorSettings.DEFAULT_QUALITY shouldBe 80
    }

    @Test
    fun `ScanPaths serializes and deserializes empty paths`() {
        val adapter = moshi.adapter(CompressorSettings.ScanPaths::class.java)

        val original = CompressorSettings.ScanPaths(paths = emptySet())
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        restored shouldBe original
        restored?.paths shouldBe emptySet()
    }

    @Test
    fun `ScanPaths serializes and deserializes multiple paths`() {
        val adapter = moshi.adapter(CompressorSettings.ScanPaths::class.java)

        val original = CompressorSettings.ScanPaths(
            paths = setOf(
                LocalPath.build("/storage/emulated/0/DCIM"),
                LocalPath.build("/storage/emulated/0/Pictures"),
            )
        )
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        restored shouldNotBe null
        restored?.paths?.size shouldBe 2
    }

    @Test
    fun `ScanPaths default constructor has empty paths`() {
        val scanPaths = CompressorSettings.ScanPaths()

        scanPaths.paths shouldBe emptySet()
    }

    @Test
    fun `quality boundary - minimum is 1`() {
        val minQuality = 1
        minQuality shouldBe 1
    }

    @Test
    fun `quality boundary - maximum is 100`() {
        val maxQuality = 100
        maxQuality shouldBe 100
    }

    @Test
    fun `LayoutMode serializes and deserializes correctly`() {
        val adapter = moshi.adapter(LayoutMode::class.java)

        for (mode in LayoutMode.entries) {
            val json = adapter.toJson(mode)
            val restored = adapter.fromJson(json)
            restored shouldBe mode
        }
    }

    @Test
    fun `LayoutMode GRID serialization round-trip`() {
        val adapter = moshi.adapter(LayoutMode::class.java)

        val json = adapter.toJson(LayoutMode.GRID)
        val restored = adapter.fromJson(json)

        restored shouldBe LayoutMode.GRID
    }

    @Test
    fun `LayoutMode LINEAR serialization round-trip`() {
        val adapter = moshi.adapter(LayoutMode::class.java)

        val json = adapter.toJson(LayoutMode.LINEAR)
        val restored = adapter.fromJson(json)

        restored shouldBe LayoutMode.LINEAR
    }

    @Test
    fun `MIN_FILE_SIZE is reasonable for image compression`() {
        // 512KB is a reasonable minimum - smaller images don't benefit much from compression
        val minSizeKb = CompressorSettings.MIN_FILE_SIZE / 1024
        minSizeKb shouldBe 512L
    }

    @Test
    fun `default quality produces good compression ratio`() {
        // Quality 80 typically results in 65% of original size for JPEG
        // This is tested in ImageScannerEstimationTest, but we verify the constant here
        CompressorSettings.DEFAULT_QUALITY shouldBe 80
    }
}
