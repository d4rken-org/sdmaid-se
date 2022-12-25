package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.json.toComparableJson
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SAFPathTest {

    val testUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Asafstor")

    @Test
    fun `test direct serialization`() {
        val original = SAFPath.build(testUri, "seg1", "seg2", "seg3")

        val adapter = SerializationModule().moshi().adapter(SAFPath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "treeRoot": "$testUri",
                "segments": ["seg1","seg2","seg3"],
                "pathType":"SAF"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
    }

    @Test
    fun `test polymorph serialization`() {
        val original = SAFPath.build(testUri, "seg3", "seg2", "seg1")

        val adapter = SerializationModule().moshi().adapter(APath::class.java)

        val json = adapter.toJson(original)
        json.toComparableJson() shouldBe """
            {
                "treeRoot": "$testUri",
                "segments": ["seg3","seg2","seg1"],
                "pathType":"SAF"
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe original
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

        val moshi = SerializationModule().moshi()

        shouldThrow<JsonDataException> {
            val json = moshi.adapter(RawPath::class.java).toJson(original)
            moshi.adapter(SAFPath::class.java).fromJson(json)
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
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup1b = SAFPathLookup(
            lookedUp = SAFPath.build(testUri, "seg1", "seg2"),
            fileType = FileType.FILE,
            size = 8,
            modifiedAt = Instant.ofEpochMilli(123),
            ownership = Ownership(1, 1),
            permissions = Permissions(444),
            target = null,
        )
        val lookup1c = SAFPathLookup(
            SAFPath.build(testUri, "seg1", "seg2"),
            fileType = FileType.DIRECTORY,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        val lookup2 = SAFPathLookup(
            lookedUp = SAFPath.build(testUri, "seg1", "test"),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            ownership = null,
            permissions = null,
            target = null,
        )
        lookup1a shouldBe lookup1b
        lookup1a shouldNotBe lookup1c
        lookup1a shouldNotBe lookup2
    }
}