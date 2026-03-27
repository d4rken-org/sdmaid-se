package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.APathSerializer
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Test
import testhelpers.json.toComparableJson
import java.io.File

class RawPathTest {
    private val json = SerializationIOModule().json()

    @Test
    fun `test polymorph serialization`() {
        val original = RawPath.build("test", "file")

        val jsonStr = json.encodeToString(APathSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "path": "test/file"
            }
        """.toComparableJson()

        json.decodeFromString(APathSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `polymorph deserialization of old JSON with pathType`() {
        val original = RawPath.build("test", "file")
        val oldJson = """{"path":"test/file","pathType":"RAW"}"""
        json.decodeFromString(APathSerializer, oldJson) shouldBe original
    }

    @Test
    fun `test direct serialization`() {
        val original = RawPath.build("test", "file")

        val jsonStr = json.encodeToString(RawPath.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "path": "test/file"
            }
        """.toComparableJson()

        json.decodeFromString(RawPath.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `test fixed type`() {
        val file = RawPath.build("test", "file")
        file.pathType shouldBe APath.PathType.RAW
        shouldThrow<IllegalArgumentException> {
            file.pathType = APath.PathType.LOCAL
            Any()
        }
        file.pathType shouldBe APath.PathType.RAW
    }

    @Test
    fun `force typing`() {
        val original = LocalPath.build(file = File("./testfile"))

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(LocalPath.serializer(), original)
            json.decodeFromString(RawPath.serializer(), jsonStr)
        }
    }
}
