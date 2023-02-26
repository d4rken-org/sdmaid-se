package eu.darken.sdmse.common.clutter.manual

import android.content.Context
import android.content.res.AssetManager
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import okio.IOException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class JsonMarkerParserTest : BaseTest() {

    private val moshi = SerializationAppModule().moshi()

    @MockK lateinit var context: Context
    @MockK lateinit var assetManager: AssetManager
    private var testData: ByteArray? = null

    @BeforeEach fun setup() {
        MockKAnnotations.init(this)
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            testData?.inputStream() ?: File(arg<String>(0)).inputStream()
        }
    }

    @AfterEach fun teardown() {
        testData = null
    }

    @Test fun `normal marker`() {
        testData = """
            [
                {
                    "pkgs": ["com.justpictures"],
                    "mrks": [{"loc": "SDCARD", "path": "JustPictures", "flags": ["keeper"]}]
                }
            ]
        """.toByteArray()

        val markerGroups = JsonMarkerParser(context, moshi).fromAssets("/data/text.json").apply {
        }
        markerGroups.size shouldBe 1


        markerGroups[0].apply {
            pkgs shouldBe setOf("com.justpictures")
            regexPkgs shouldBe null
            mrks.size shouldBe 1
        }

        markerGroups[0].mrks[0].apply {
            areaType shouldBe DataArea.Type.SDCARD

            regex shouldBe null
            contains shouldBe null

            path shouldBe "JustPictures"
            flags shouldBe setOf(Marker.Flag.KEEPER)
        }
    }

    @Test fun `regex marker`() {
        testData = """
            [
                {
                    "pkgs": ["com.justpictures"],
                    "mrks": [
                        {"loc": "SDCARD", "path": "data/com.teslacoilsw.launcher", "flags": ["keeper"]},
                        {"loc": "SDCARD", "contains": ".novabackup", "regex": "^(?:Backup/.+?\\.novabackup)${'$'}", "flags": ["keeper"]}
                    ]
                }
            ]
        """.toByteArray()

        val markerGroups = JsonMarkerParser(context, moshi).fromAssets("/data/text.json").apply {
        }
        markerGroups.size shouldBe 1


        markerGroups[0].apply {
            pkgs shouldBe setOf("com.justpictures")
            regexPkgs shouldBe null
            mrks.size shouldBe 2
        }

        markerGroups[0].mrks[0].apply {
            areaType shouldBe DataArea.Type.SDCARD

            regex shouldBe null
            contains shouldBe null

            path shouldBe "data/com.teslacoilsw.launcher"
            flags shouldBe setOf(Marker.Flag.KEEPER)
        }

        markerGroups[0].mrks[1].apply {
            areaType shouldBe DataArea.Type.SDCARD

            regex shouldBe "^(?:Backup/.+?\\.novabackup)\$"
            contains shouldBe ".novabackup"

            path shouldBe null
            flags shouldBe setOf(Marker.Flag.KEEPER)
        }
    }

    @Test fun `invalid group`() {
        testData = """
            [
                {
                    "mrks": [{"loc": "SDCARD", "path": "JustPictures", "flags": ["keeper"]}]
                }
            ]
        """.toByteArray()
        shouldThrow<IOException> {
            JsonMarkerParser(context, moshi).fromAssets("/data/text.json")
        }
    }

    @Test fun `empty marker`() {
        testData = """
            [
                {
                    "pkgs": ["eu.darken.sdmse.justpictures"],
                    "mrks": []
                }
            ]
        """.toByteArray()
        shouldThrow<IOException> {
            JsonMarkerParser(context, moshi).fromAssets("/data/text.json")
        }
    }


    @Test fun `missing marker`() {
        testData = """
            [
                {
                    "pkgs": ["eu.darken.sdmse.justpictures"],
                    "mrks": []
                }
            ]
        """.toByteArray()
        shouldThrow<IOException> {
            JsonMarkerParser(context, moshi).fromAssets("/data/text.json")
        }
    }

    @Test fun `invalid marker`() {
        testData = """
            [
                {
                    "pkgs": ["eu.darken.sdmse.justpictures"],
                    "mrks": [{"loc": "SDCARD", "flags": ["keeper"]}]
                }
            ]
        """.toByteArray()
        shouldThrow<IOException> {
            JsonMarkerParser(context, moshi).fromAssets("/data/text.json")
        }
    }

    @Test fun `parse debug set`() {
        JsonMarkerParser(context, moshi).fromAssets("./src/test/assets/clutter/db_debug_markers.json").apply {
            isEmpty() shouldBe false
        }
    }

    @Test fun `parse prod set`() {
        JsonMarkerParser(context, moshi).fromAssets("./src/main/assets/clutter/db_clutter_markers.json").apply {
            isEmpty() shouldBe false
            (size > 1000) shouldBe true
        }
    }

}