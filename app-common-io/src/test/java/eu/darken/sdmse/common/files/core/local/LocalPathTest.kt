package eu.darken.sdmse.common.files.core.local

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.RawPath
import eu.darken.sdmse.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.json.toComparableJson
import java.io.File

class LocalPathTest {
    private val testFile = File("./testfile")

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `test direct serialization`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val adapter = SerializationModule().moshi().adapter(LocalPath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "file": "$testFile",
                "pathType":"LOCAL"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `test polymorph serialization`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val adapter = SerializationModule().moshi().adapter(APath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "file":"$testFile",
                "pathType":"LOCAL"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `test fixed type`() {
        val file = LocalPath(testFile)
        file.pathType shouldBe APath.PathType.LOCAL
        shouldThrow<IllegalArgumentException> {
            file.pathType = APath.PathType.RAW
            Any()
        }
        file.pathType shouldBe APath.PathType.LOCAL
    }

    @Test
    fun `force typing`() {
        val original = RawPath.build("test", "file")

        val moshi = SerializationModule().moshi()

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(RawPath::class.java).toJson(original)
            moshi.adapter(LocalPath::class.java).fromJson(json)
        }
    }
}