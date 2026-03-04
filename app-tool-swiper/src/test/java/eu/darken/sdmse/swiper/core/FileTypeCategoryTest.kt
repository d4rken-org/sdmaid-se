package eu.darken.sdmse.swiper.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FileTypeCategoryTest : BaseTest() {

    @Test
    fun `fromExtension returns correct category for known extensions`() {
        FileTypeCategory.fromExtension("jpg") shouldBe FileTypeCategory.IMAGES
        FileTypeCategory.fromExtension("png") shouldBe FileTypeCategory.IMAGES
        FileTypeCategory.fromExtension("heic") shouldBe FileTypeCategory.IMAGES

        FileTypeCategory.fromExtension("mp4") shouldBe FileTypeCategory.VIDEOS
        FileTypeCategory.fromExtension("mkv") shouldBe FileTypeCategory.VIDEOS

        FileTypeCategory.fromExtension("mp3") shouldBe FileTypeCategory.AUDIO
        FileTypeCategory.fromExtension("flac") shouldBe FileTypeCategory.AUDIO

        FileTypeCategory.fromExtension("pdf") shouldBe FileTypeCategory.DOCUMENTS
        FileTypeCategory.fromExtension("docx") shouldBe FileTypeCategory.DOCUMENTS

        FileTypeCategory.fromExtension("zip") shouldBe FileTypeCategory.ARCHIVES
        FileTypeCategory.fromExtension("apk") shouldBe FileTypeCategory.ARCHIVES
    }

    @Test
    fun `fromExtension returns null for unknown extensions`() {
        FileTypeCategory.fromExtension("xyz").shouldBeNull()
        FileTypeCategory.fromExtension("foo").shouldBeNull()
        FileTypeCategory.fromExtension("").shouldBeNull()
    }

    @Test
    fun `fromExtension is case insensitive`() {
        FileTypeCategory.fromExtension("JPG") shouldBe FileTypeCategory.IMAGES
        FileTypeCategory.fromExtension("Png") shouldBe FileTypeCategory.IMAGES
        FileTypeCategory.fromExtension("MP4") shouldBe FileTypeCategory.VIDEOS
    }
}
