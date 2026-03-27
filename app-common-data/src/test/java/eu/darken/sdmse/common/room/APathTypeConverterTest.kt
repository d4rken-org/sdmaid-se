package eu.darken.sdmse.common.room

import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class APathTypeConverterTest : BaseTest() {

    private val json = SerializationIOModule().json()
    private val converter = APathTypeConverter(json)

    @Test
    fun `LocalPath serialize matches golden JSON`() {
        val path = LocalPath.build(file = File("/data/user/0/eu.darken.sdmse/cache"))

        val jsonStr = converter.from(path)

        jsonStr.toComparableJson() shouldBe """
            {
                "file": "/data/user/0/eu.darken.sdmse/cache"
            }
        """.toComparableJson()
    }

    @Test
    fun `LocalPath deserialize from golden JSON`() {
        val jsonStr = """{"file":"/data/user/0/eu.darken.sdmse/cache","pathType":"LOCAL"}"""
        val path = converter.to(jsonStr)
        path shouldBe LocalPath.build(file = File("/data/user/0/eu.darken.sdmse/cache"))
    }

    @Test
    fun `RawPath serialize matches golden JSON`() {
        val path = RawPath.build("/storage/emulated/0/DCIM")

        val jsonStr = converter.from(path)

        jsonStr.toComparableJson() shouldBe """
            {
                "path": "/storage/emulated/0/DCIM"
            }
        """.toComparableJson()
    }

    @Test
    fun `RawPath deserialize from golden JSON`() {
        val jsonStr = """{"path":"/storage/emulated/0/DCIM","pathType":"RAW"}"""
        val path = converter.to(jsonStr)
        path shouldBe RawPath.build("/storage/emulated/0/DCIM")
    }

    @Test
    fun `round trip preserves LocalPath`() {
        val original = LocalPath.build(file = File("/test/path"))
        val jsonStr = converter.from(original)
        val restored = converter.to(jsonStr)
        restored shouldBe original
    }

    @Test
    fun `round trip preserves RawPath`() {
        val original = RawPath.build("/raw/path")
        val jsonStr = converter.from(original)
        val restored = converter.to(jsonStr)
        restored shouldBe original
    }
}
