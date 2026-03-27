package eu.darken.sdmse.exclusion.core.types

import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.pkgs.toPkgId
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

class PkgExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val json = SerializationIOModule().json()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `match package`() = runTest {
        val excl = PkgExclusion("test.package".toPkgId())
        excl.match("test.package".toPkgId()) shouldBe true
        excl.match("testpackage".toPkgId()) shouldBe false
        excl.match("".toPkgId()) shouldBe false
    }

    @Test
    fun `custom tags`() {
        testFile.tryMkFile()
        val original = PkgExclusion(
            pkgId = "test.pkg".toPkgId(),
            tags = setOf(Exclusion.Tag.GENERAL, Exclusion.Tag.APPCLEANER)
        )

        val jsonStr = json.encodeToString(PkgExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
                },
                "tags": [
                    "GENERAL", "APPCLEANER"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(PkgExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `direct serialization`() {
        testFile.tryMkFile()
        val original = PkgExclusion("test.pkg".toPkgId())

        val jsonStr = json.encodeToString(PkgExclusion.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
                },
                "tags": [
                    "GENERAL"
                ]
            }
        """.toComparableJson()

        json.decodeFromString(PkgExclusion.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `polymorph serialization`() {
        testFile.tryMkFile()
        val original = PkgExclusion("test.pkg".toPkgId())

        val jsonStr = json.encodeToString(ExclusionSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
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
        val original = PkgExclusion("test.pkg".toPkgId())

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(PkgExclusion.serializer(), original)
            json.decodeFromString(PathExclusion.serializer(), jsonStr)
        }
    }
}
