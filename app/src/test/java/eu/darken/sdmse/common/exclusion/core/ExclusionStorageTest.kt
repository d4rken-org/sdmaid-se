package eu.darken.sdmse.common.exclusion.core

import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.RawPath
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.local.tryMkFile
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.json.toComparableJson
import java.io.File

class ExclusionStorageTest {
    private val testFile = File("./testfile")
    private val moshi = SerializationAppModule().moshi()

    @AfterEach
    fun cleanup() {
        testFile.delete()
    }

    @Test
    fun `test direct serialization`() {
        testFile.tryMkFile()
        val original = PackageExclusion(
            pkgId = "test.pkg".toPkgId()
        )

        val adapter = moshi.adapter(Exclusion::class.java)

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

        val adapter = moshi.adapter(APath::class.java)

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

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(RawPath::class.java).toJson(original)
            moshi.adapter(LocalPath::class.java).fromJson(json)
        }
    }
}