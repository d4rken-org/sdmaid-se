package eu.darken.sdmse.common.exclusion.core.types

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.local.tryMkFile
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import java.io.File

class PkgExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val moshi = SerializationAppModule().moshi()

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

        val adapter = moshi.adapter(PkgExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
                },
                "tags": [
                    "GENERAL", "APPCLEANER"
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `direct serialization`() {
        testFile.tryMkFile()
        val original = PkgExclusion("test.pkg".toPkgId())

        val adapter = moshi.adapter(PkgExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
                },
                "tags": [
                    "GENERAL"
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `polymorph serialization`() {
        testFile.tryMkFile()
        val original = PkgExclusion("test.pkg".toPkgId())

        val adapter = moshi.adapter(Exclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "pkgId": {
                    "name": "test.pkg"
                },
                "tags": [
                    "GENERAL"
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `force typing`() {
        val original = PkgExclusion("test.pkg".toPkgId())

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(PkgExclusion::class.java).toJson(original)
            moshi.adapter(PathExclusion::class.java).fromJson(json)
        }
    }
}