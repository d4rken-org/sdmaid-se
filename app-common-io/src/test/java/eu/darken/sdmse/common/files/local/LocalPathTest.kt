package eu.darken.sdmse.common.files.local

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.serialization.APathSerializer
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File
import java.time.Instant

class LocalPathTest : BaseTest() {
    private val testFile = File("./testfile")
    private val testFile2 = File("./testfile2")

    private val json = SerializationIOModule().json()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `direct serialization with transient fields`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        // segmentsCache needs to be ignored during serialization
        println(original.segments.toString())

        val jsonStr = json.encodeToString(LocalPath.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "file": "${testFile.path}"
            }
        """.toComparableJson()

        json.decodeFromString(LocalPath.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `deserialization of old JSON with pathType and transient fields`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val jsonStr = """
            {
                "file": "${testFile.path}",
                "pathType":"LOCAL",
                "segmentsCache": [
                    ".",
                    "testfile"
                ]
            }
        """

        json.decodeFromString(LocalPath.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `test polymorph serialization`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)

        val jsonStr = json.encodeToString(APathSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "file":"${testFile.path}"
            }
        """.toComparableJson()

        json.decodeFromString(APathSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `polymorph deserialization of old JSON with pathType`() {
        testFile.tryMkFile()
        val original = LocalPath.build(file = testFile)
        val oldJson = """{"file":"${testFile.path}","pathType":"LOCAL"}"""
        json.decodeFromString(APathSerializer, oldJson) shouldBe original
    }

    @Test
    fun `test polymorph list serialization`() {
        testFile.tryMkFile()
        val original = listOf(
            LocalPath.build(file = testFile),
            LocalPath.build(file = testFile2),
        )

        val listSerializer = ListSerializer(APathSerializer)
        val jsonStr = json.encodeToString(listSerializer, original)

        jsonStr.toComparableJson() shouldBe """
                [
                    {
                        "file":"${testFile.path}"
                    }, {
                        "file":"${testFile2.path}"
                    }
                ]
        """.toComparableJson()

        json.decodeFromString(listSerializer, jsonStr) shouldBe original
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

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(RawPath.serializer(), original)
            json.decodeFromString(LocalPath.serializer(), jsonStr)
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
