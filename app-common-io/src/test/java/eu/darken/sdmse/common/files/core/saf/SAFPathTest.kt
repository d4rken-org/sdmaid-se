package eu.darken.sdmse.common.files.core.saf

import android.net.Uri
import com.squareup.moshi.JsonDataException
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.RawPath
import eu.darken.sdmse.common.serialization.SerializationModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.json.toComparableJson

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
}