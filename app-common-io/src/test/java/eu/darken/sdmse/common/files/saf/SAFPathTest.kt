package eu.darken.sdmse.common.files.saf

import android.net.Uri
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.serialization.APathSerializer
import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.json.toComparableJson

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SAFPathTest : BaseTest() {

    val testUri = "content://com.android.externalstorage.documents/tree/primary%3Asafstor"

    private val json = SerializationIOModule().json()

    @Test
    fun `test direct serialization`() {
        val original = SAFPath.build(testUri, "seg1", "seg2", "seg3")

        val jsonStr = json.encodeToString(SAFPath.serializer(), original)
        jsonStr.toComparableJson() shouldBe """
            {
                "treeRoot": "$testUri",
                "segments": ["seg1","seg2","seg3"]
            }
        """.toComparableJson()

        json.decodeFromString(SAFPath.serializer(), jsonStr) shouldBe original
    }

    @Test
    fun `test polymorph serialization`() {
        val original = SAFPath.build(testUri, "seg3", "seg2", "seg1")

        val jsonStr = json.encodeToString(APathSerializer, original)
        jsonStr.toComparableJson() shouldBe """
            {
                "treeRoot": "$testUri",
                "segments": ["seg3","seg2","seg1"]
            }
        """.toComparableJson()

        json.decodeFromString(APathSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `polymorph deserialization of old JSON with pathType`() {
        val original = SAFPath.build(testUri, "seg1", "seg2")
        val oldJson = """{"treeRoot":"$testUri","segments":["seg1","seg2"],"pathType":"SAF"}"""
        json.decodeFromString(APathSerializer, oldJson) shouldBe original
    }

    @Test
    fun `test polymorph list serialization`() {
        val original = listOf(
            SAFPath.build(testUri, "seg3", "seg2", "seg1"),
            SAFPath.build(testUri, "seg4", "seg5", "seg6"),
        )

        val listSerializer = ListSerializer(APathSerializer)
        val jsonStr = json.encodeToString(listSerializer, original)

        jsonStr.toComparableJson() shouldBe """
                [
                    {
                        "treeRoot": "$testUri",
                        "segments": ["seg3","seg2","seg1"]
                    }, {
                        "treeRoot": "$testUri",
                        "segments": ["seg4","seg5","seg6"]
                    }
                ]
        """.toComparableJson()

        json.decodeFromString(listSerializer, jsonStr) shouldBe original
    }

    @Test
    fun `test fixed type`() {
        val file = SAFPath.build(testUri, "seg1", "seg2")
        file.pathType shouldBe APath.PathType.SAF
        shouldThrow<java.lang.IllegalArgumentException> {
            file.pathType = APath.PathType.LOCAL
            Any()
        }
        file.pathType shouldBe APath.PathType.SAF
    }

    @Test
    fun `test must be tree uri`() {
        shouldThrow<IllegalArgumentException> {
            SAFPath.build(Uri.parse("abc"))
        }
    }

    @Test
    fun `force typing`() {
        val original = RawPath.build("test", "file")

        shouldThrow<SerializationException> {
            val jsonStr = json.encodeToString(RawPath.serializer(), original)
            json.decodeFromString(SAFPath.serializer(), jsonStr)
        }
    }

    @Test
    fun `path comparison`() {
        val file1a = SAFPath.build(testUri, "seg1", "seg2")
        val file1b = SAFPath.build(testUri, "seg1", "seg2")
        val file2 = SAFPath.build(testUri, "seg1", "test")
        file1a shouldBe file1b
        file1a shouldNotBe file2
    }

    @Test
    fun `lookup comparison`() {
        val lookup1a = SAFPathLookup(
            lookedUp = SAFPath.build(testUri, "seg1", "seg2"),
            docFile = mockk<SAFDocFile>().apply {
            }
        )
        val lookup1b = SAFPathLookup(
            lookedUp = SAFPath.build(testUri, "seg1", "seg2"),
            docFile = mockk<SAFDocFile>().apply {
            }
        )
        val lookup1c = SAFPathLookup(
            SAFPath.build(testUri, "seg1", "seg2"),
            docFile = mockk<SAFDocFile>().apply {
            }
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(testUri, "seg1", "test"),
            docFile = mockk<SAFDocFile>().apply {
            }
        )
        lookup1a shouldNotBe lookup1b
        lookup1a shouldNotBe lookup1c
        lookup1a shouldNotBe lookup2
    }

    @Test
    fun `user readable path mapping`() {
        SAFPath.build(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor"),
            "seg1",
            "seg2",
        ).userReadablePath.get(mockk()) shouldBe "/storage/emulated/0/seg1/seg2"
        SAFPath.build(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary"),
            "seg1",
            "seg2",
        ).userReadablePath.get(mockk()) shouldBe "/storage/emulated/0/seg1/seg2"
        SAFPath.build(
            Uri.parse("content://com.android.externalstorage.documents/tree/3135-3132%3Asafstor"),
            "seg1",
            "seg2",
        ).userReadablePath.get(mockk()) shouldBe "/storage/3135-3132/seg1/seg2"
        SAFPath.build(
            Uri.parse("content://com.android.externalstorage.documents/tree/3135-3132"),
            "seg1",
            "seg2",
        ).userReadablePath.get(mockk()) shouldBe "/storage/3135-3132/seg1/seg2"
    }
}
