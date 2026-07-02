package eu.darken.sdmse.squeezer.core

import android.graphics.Bitmap
import android.os.Build
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/** Robolectric-driven so `Build.VERSION.SDK_INT` matches the configured API level. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], application = TestApplication::class)
class CompressibleImageHeicTest : BaseTest() {

    @Test
    fun `HEIC_MIME_TYPES covers heic and heif`() {
        CompressibleImage.HEIC_MIME_TYPES shouldContainAll setOf(
            CompressibleImage.MIME_TYPE_HEIC,
            CompressibleImage.MIME_TYPE_HEIF,
        )
    }

    @Test
    fun `SUPPORTED_MIME_TYPES includes HEIC mimes`() {
        CompressibleImage.SUPPORTED_MIME_TYPES shouldContainAll setOf(
            CompressibleImage.MIME_TYPE_JPEG,
            CompressibleImage.MIME_TYPE_WEBP,
            CompressibleImage.MIME_TYPE_HEIC,
            CompressibleImage.MIME_TYPE_HEIF,
        )
    }

    @Test
    fun `compressFormat throws for HEIC mimes`() {
        shouldThrow<IllegalArgumentException> {
            CompressibleImage.compressFormat(CompressibleImage.MIME_TYPE_HEIC)
        }
        shouldThrow<IllegalArgumentException> {
            CompressibleImage.compressFormat(CompressibleImage.MIME_TYPE_HEIF)
        }
    }

    @Test
    fun `compressFormat still works for JPEG and WebP`() {
        CompressibleImage.compressFormat(CompressibleImage.MIME_TYPE_JPEG) shouldBe Bitmap.CompressFormat.JPEG
        // WEBP_LOSSY on R+ in compressFormat; on P this returns WEBP.
        @Suppress("DEPRECATION")
        CompressibleImage.compressFormat(CompressibleImage.MIME_TYPE_WEBP) shouldBe Bitmap.CompressFormat.WEBP
    }

    @Test
    fun `isHeicEncodingSupported is true on API 28`() {
        CompressibleImage.isHeicEncodingSupported() shouldBe true
    }

    @Test
    fun `deviceSupportedMimeTypes includes HEIC on P+`() {
        CompressibleImage.deviceSupportedMimeTypes() shouldContainAll CompressibleImage.HEIC_MIME_TYPES
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = TestApplication::class)
class CompressibleImageHeicPreP : BaseTest() {

    @Test
    fun `isHeicEncodingSupported is false on API 27`() {
        CompressibleImage.isHeicEncodingSupported() shouldBe false
    }

    @Test
    fun `deviceSupportedMimeTypes excludes HEIC on pre-P`() {
        val mimes = CompressibleImage.deviceSupportedMimeTypes()
        mimes shouldNotContain CompressibleImage.MIME_TYPE_HEIC
        mimes shouldNotContain CompressibleImage.MIME_TYPE_HEIF
    }
}
