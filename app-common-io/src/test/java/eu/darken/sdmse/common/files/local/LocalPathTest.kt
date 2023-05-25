package eu.darken.sdmse.common.files.local

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File
import java.time.Instant

class LocalPathTest : BaseTest() {
    private val testFile = File("./testfile")

    private val moshi = SerializationIOModule().moshi()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `test direct serialization`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val adapter = moshi.adapter(LocalPath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "file": "${testFile.path}",
                "pathType":"LOCAL"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `test polymorph serialization`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val adapter = moshi.adapter(APath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "file":"${testFile.path}",
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

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(RawPath::class.java).toJson(original)
            moshi.adapter(LocalPath::class.java).fromJson(json)
        }
    }

    @Test
    fun `path are always absolute`() {
        LocalPath.build("test", "file1").path shouldBe "/test/file1"
        LocalPath.build("").path shouldBe "/"
    }

    @Test
    fun `segment generation`() {
        LocalPath.build("a", "b", "c").segments shouldBe listOf("", "a", "b", "c")
        LocalPath.build().segments shouldBe listOf("")
    }

    @Test
    fun `parent generation`() {
        LocalPath.build("a", "b", "c").parent() shouldBe LocalPath.build("a", "b")
        LocalPath.build("a").parent() shouldBe LocalPath.build()
        LocalPath.build().parent() shouldBe null
    }

    @Test
    fun `path comparison`() {
        val file1a = LocalPath.build("test", "file1")
        val file1b = LocalPath.build("test", "file1")
        val file2 = LocalPath.build("test", "file2")
        file1a shouldBe file1b
        file1a shouldNotBe file2
    }

    @Test
    fun `lookup comparison`() {
        val lookup1a = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup1b = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file1"),
            fileType = FileType.FILE,
            size = 8,
            modifiedAt = Instant.ofEpochMilli(123),
            target = null,
        )
        val lookup1c = LocalPathLookup(
            LocalPath.build("test", "file1"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        val lookup2 = LocalPathLookup(
            lookedUp = LocalPath.build("test", "file2"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        )
        lookup1a shouldNotBe lookup1b
        lookup1a shouldNotBe lookup1c
        lookup1a shouldNotBe lookup2
    }
}