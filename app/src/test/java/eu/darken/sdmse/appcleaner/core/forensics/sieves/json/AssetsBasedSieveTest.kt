package eu.darken.sdmse.appcleaner.core.forensics.sieves.json

import android.content.Context
import android.content.res.AssetManager
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.segs
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class AssetsBasedSieveTest : BaseTest() {
    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()
    private var testData: ByteArray? = null
    val baseMoshi = SerializationAppModule().moshi()

    @BeforeEach
    fun setup() {
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            testData?.inputStream() ?: File(arg<String>(0)).inputStream()
        }
    }

    fun create(path: String): JsonBasedSieve = JsonBasedSieve(path, context, baseMoshi)

    @Test fun `invalid empty file`() {
        shouldThrowAny {
            testData = """
                {

                }
        """.trimIndent().toByteArray()
            create("")
        }
        shouldThrowAny {
            testData = """
            {
              "schemaVersion": 1,
              "appFilter": []
            }
        """.trimIndent().toByteArray()
            create("")
        }
        shouldThrowAny {
            testData = """
            {
              "schemaVersion": 1
            }
        """.trimIndent().toByteArray()
            create("")
        }
    }

    @Test fun `invalid app filter`() {
        shouldThrowAny {
            testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": []
                    }
                  ]
                }
        """.trimIndent().toByteArray()
            create("")
        }
        shouldThrowAny {
            testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      
                    }
                  ]
                }
        """.trimIndent().toByteArray()
            create("")
        }
    }

    @Test fun `invalid file filter`() {
        shouldThrowAny {
            testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "contains": ["mologiq"],
                          "patterns": ["^(?:[\\w_\\-\\.]+?)(?>/files/\\.[a-f0-9\\-]+?\\.mologiq)${'$'}", "^(?:[\\w_\\-\\.]+?)(?>/databases/mologiq)${'$'}"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()
            create("")
        }
        shouldThrowAny {
            testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["PRIVATE_DATA"],
                          "patterns": ["^(?:[\\w_\\-\\.]+?)(?>/files/\\.[a-f0-9\\-]+?\\.mologiq)${'$'}", "^(?:[\\w_\\-\\.]+?)(?>/databases/mologiq)${'$'}"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()
            create("")
        }
    }

    @Test fun `asset loading`() {
        create("./src/test/assets/expendables/db_debug_files.json")
    }


    @Test fun `location condition`() {
        testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["SDCARD"],
                          "contains": ["a/test/path"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()

        create("").apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("a", "test", "path")
            ) shouldBe false

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true
        }
    }

    @Test fun testBadMatch() {
        testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["SDCARD"],
                          "contains": ["a/test/path"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()

        create("").apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("another", "test", "path")
            ) shouldBe false
        }
    }

    @Test fun testCaseSensitivity() {
        testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["SDCARD","PRIVATE_DATA"],
                          "startsWith": ["a/test/path"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()

        create("").apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("A", "test", "PATH")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("A", "test", "PATH")
            ) shouldBe false
        }
    }

    @Test fun testStartsWith() {
        testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["SDCARD"],
                          "startsWith": ["a/test/path"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()

        create("").apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("not", "a", "test", "path")
            ) shouldBe false
        }
    }

    @Test fun testContains() {
        testData = """
                {
                  "schemaVersion": 1,
                  "appFilter": [
                    {
                      "fileFilter": [
                        {
                          "locations": ["SDCARD","PRIVATE_DATA","SYSTEM"],
                          "contains": ["a/test/path"]
                        }
                      ]
                    }
                  ]
                }
        """.trimIndent().toByteArray()

        create("").apply {
            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SDCARD,
                target = segs("a", "test", "path")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.PRIVATE_DATA,
                target = segs("a", "test", "path", "file")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("aaa", "test", "pathhhh")
            ) shouldBe true

            matches(
                pkgId = "any.pkg".toPkgId(),
                areaType = DataArea.Type.SYSTEM,
                target = segs("123")
            ) shouldBe false
        }
    }
}