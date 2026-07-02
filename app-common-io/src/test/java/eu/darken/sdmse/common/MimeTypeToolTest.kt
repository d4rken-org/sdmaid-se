package eu.darken.sdmse.common

import eu.darken.sdmse.common.files.APathLookup
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class MimeTypeToolTest : BaseTest() {

    private val subject = MimeTypeTool()

    private fun lookupWithName(name: String): APathLookup<*> = mockk(relaxed = true) {
        every { this@mockk.name } returns name
    }

    @Test
    fun `jpg resolves to image jpeg`() {
        runBlocking {
            subject.determineMimeType(lookupWithName("photo.jpg")) shouldBe "image/jpeg"
        }
    }

    @Test
    fun `webp resolves to image webp`() {
        runBlocking {
            subject.determineMimeType(lookupWithName("photo.webp")) shouldBe "image/webp"
        }
    }

    @Test
    fun `heic resolves to image heic via MimeTypeMap or fallback`() {
        runBlocking {
            subject.determineMimeType(lookupWithName("portrait.heic")) shouldBe "image/heic"
        }
    }

    @Test
    fun `heif resolves to image heif via MimeTypeMap or fallback`() {
        runBlocking {
            subject.determineMimeType(lookupWithName("portrait.heif")) shouldBe "image/heif"
        }
    }

    @Test
    fun `unknown extension yields Unknown mime`() {
        runBlocking {
            subject.determineMimeType(lookupWithName("data.zzdefinitelynotaformat")) shouldBe MimeTypes.Unknown.value
        }
    }
}
