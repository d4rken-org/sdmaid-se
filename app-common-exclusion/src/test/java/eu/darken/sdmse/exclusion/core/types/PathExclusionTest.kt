package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class PathExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val json = SerializationIOModule().json()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `match local path`() = runTest {
        val excl = PathExclusion(LocalPath.build("test", "path"))
        excl.match(LocalPath.build("test", "path")) shouldBe true
        excl.match(LocalPath.build("testpath")) shouldBe false
        excl.match(LocalPath.build()) shouldBe false
    }

    @Test
    fun `custom tags`() {
        testFile.tryMkFile()
        val original = PathExclusion(
            path = LocalPath.build("test", "path"),
            tags = setOf(Exclusion.Tag.DEDUPLICATOR, Exclusion.Tag.APPCLEANER)
        )

        val jsonStr = json.encodeToString(PathExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "path": {
                    "file": "/test/path",
                    "pathType": "LOCAL"
                },
                "tags": [
                    "DEDUPLICATOR",
                    "APPCLEANER"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(PathExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `direct serialization`() {
        testFile.tryMkFile()
        val original = PathExclusion(LocalPath.build("test", "path"))

        val jsonStr = json.encodeToString(PathExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "path": {
                    "file": "/test/path",
                    "pathType": "LOCAL"
                },
                "tags": [
                    "GENERAL"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(PathExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `polymorph serialization`() {
        testFile.tryMkFile()
        val original = PathExclusion(LocalPath.build("test", "path"))

        val jsonStr = json.encodeToString(ExclusionSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "path": {
                    "file": "/test/path",
                    "pathType": "LOCAL"
                },
                "tags": [
                    "GENERAL"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(ExclusionSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `force typing`() {
        val original = PathExclusion(LocalPath.build("test", "path"))

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(PathExclusion.serializer(), original)
            json.decodeFromString(PkgExclusion.serializer(), jsonStr)
        }
    }
}
