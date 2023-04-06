package eu.darken.sdmse.common.files

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.json.toComparableJson
import java.io.File

class RawPathTest {
    private val baseMoshi = SerializationIOModule().moshi()

    @Test
    fun `test polymorph serialization`() {
        val original = RawPath.build("test", "file")

        val adapter = baseMoshi.adapter(APath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "path": "test/file",
                "pathType": "RAW"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `test direct serialization`() {
        val original = RawPath.build("test", "file")

        val adapter = baseMoshi.adapter(RawPath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "path": "test/file",
                "pathType": "RAW"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
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

        val moshi = baseMoshi

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(LocalPath::class.java).toJson(original)
            moshi.adapter(RawPath::class.java).fromJson(json)
        }
    }
}