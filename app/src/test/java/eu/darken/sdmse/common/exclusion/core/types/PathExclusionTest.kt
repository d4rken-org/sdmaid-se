package eu.darken.sdmse.common.exclusion.core.types

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.tryMkFile
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

class PathExclusionTest : BaseTest() {
    private val testFile = File(IO_TEST_BASEDIR, "testfile")
    private val moshi = SerializationAppModule().moshi()

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
            tags = setOf(Exclusion.Tag.GENERAL, Exclusion.Tag.APPCLEANER)
        )

        val adapter = moshi.adapter(PathExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "path": {
                    "file": "/test/path",
                    "pathType": "LOCAL"
                },
                "tags": [
                    "GENERAL",
                    "APPCLEANER"
                ]
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `direct serialization`() {
        testFile.tryMkFile()
        val original = PathExclusion(LocalPath.build("test", "path"))

        val adapter = moshi.adapter(PathExclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `polymorph serialization`() {
        testFile.tryMkFile()
        val original = PathExclusion(LocalPath.build("test", "path"))

        val adapter = moshi.adapter(Exclusion::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
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

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `force typing`() {
        val original = PathExclusion(LocalPath.build("test", "path"))

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(PathExclusion::class.java).toJson(original)
            moshi.adapter(PkgExclusion::class.java).fromJson(json)
        }
    }
}