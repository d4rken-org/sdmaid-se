package eu.darken.sdmse.common.files.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.darken.sdmse.common.files.*
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SAFDocFileTest : BaseTest() {
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    @Test
    fun `test tree uri no segments`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            emptyList()
        ) shouldBe Uri.parse("content://auth.ority/tree/primary%3A/document/primary%3A")

        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            emptyList()
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A"
    }

    @Test
    fun `test tree uri 1 segment`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("segment1")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Fsegment1"
    }

    @Test
    fun `test tree uri 2 segments`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("segment1", "segment2")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2Fsegment1%2Fsegment2"
    }

    @Test
    fun `test tree uri 2 empty segment`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://auth.ority/tree/primary%3A"),
            listOf("")
        ).toString() shouldBe "content://auth.ority/tree/primary%3A/document/primary%3A%2F"
    }

    @Test
    fun `test tree seperator addition`() {
        SAFDocFile.buildTreeUri(
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata"),
            listOf("com.samsung.android.smartmirroring")
        )
            .toString() shouldBe "content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata/document/primary%3AAndroid%2Fdata%2Fcom.samsung.android.smartmirroring"
    }

//    @Test
//    fun `docfile instantiation`() {
//        val fileUri = Uri.parse("content://auth.ority/tree/primary%3A/document/primary%3Asegment1")
//        SAFDocFile.fromTreeUri(
//            context,
//            contentResolver,
//            fileUri
//        ).toString() shouldBe "SAFDocFile(uri=$fileUri)"
//    }
}