package eu.darken.sdmse.swiper.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FileTypeFilterTest : BaseTest() {

    @Test
    fun `empty filter matches all extensions`() {
        val filter = FileTypeFilter.EMPTY
        filter.isEmpty shouldBe true
        filter.matchesExtension("jpg") shouldBe true
        filter.matchesExtension("xyz") shouldBe true
        filter.matchesExtension("") shouldBe true
    }

    @Test
    fun `category filter matches extensions in category`() {
        val filter = FileTypeFilter(categories = setOf(FileTypeCategory.IMAGES))
        filter.isEmpty shouldBe false
        filter.matchesExtension("jpg") shouldBe true
        filter.matchesExtension("png") shouldBe true
        filter.matchesExtension("mp4") shouldBe false
        filter.matchesExtension("pdf") shouldBe false
    }

    @Test
    fun `multiple categories filter`() {
        val filter = FileTypeFilter(categories = setOf(FileTypeCategory.IMAGES, FileTypeCategory.VIDEOS))
        filter.matchesExtension("jpg") shouldBe true
        filter.matchesExtension("mp4") shouldBe true
        filter.matchesExtension("mp3") shouldBe false
    }

    @Test
    fun `custom extensions filter`() {
        val filter = FileTypeFilter(customExtensions = setOf("apk", "log"))
        filter.isEmpty shouldBe false
        filter.matchesExtension("apk") shouldBe true
        filter.matchesExtension("log") shouldBe true
        filter.matchesExtension("jpg") shouldBe false
    }

    @Test
    fun `combined category and custom filter`() {
        val filter = FileTypeFilter(
            categories = setOf(FileTypeCategory.IMAGES),
            customExtensions = setOf("log"),
        )
        filter.matchesExtension("jpg") shouldBe true
        filter.matchesExtension("log") shouldBe true
        filter.matchesExtension("mp4") shouldBe false
    }

    @Test
    fun `matchesExtension is case insensitive`() {
        val filter = FileTypeFilter(categories = setOf(FileTypeCategory.IMAGES))
        filter.matchesExtension("JPG") shouldBe true
        filter.matchesExtension("Png") shouldBe true
    }

    @Test
    fun `custom extension matching is case insensitive`() {
        val filter = FileTypeFilter(customExtensions = setOf("apk"))
        filter.matchesExtension("APK") shouldBe true
        filter.matchesExtension("Apk") shouldBe true
    }

    @Test
    fun `non-matching extension returns false`() {
        val filter = FileTypeFilter(categories = setOf(FileTypeCategory.IMAGES))
        filter.matchesExtension("xyz") shouldBe false
        filter.matchesExtension("") shouldBe false
    }

    @Test
    fun `parseCustomExtensions handles various formats`() {
        FileTypeFilter.parseCustomExtensions("apk, log, txt")
            .shouldContainExactlyInAnyOrder("apk", "log", "txt")
    }

    @Test
    fun `parseCustomExtensions strips dots`() {
        FileTypeFilter.parseCustomExtensions(".apk .log")
            .shouldContainExactlyInAnyOrder("apk", "log")
    }

    @Test
    fun `parseCustomExtensions handles spaces and commas`() {
        FileTypeFilter.parseCustomExtensions("  apk , , log  ")
            .shouldContainExactlyInAnyOrder("apk", "log")
    }

    @Test
    fun `parseCustomExtensions handles blank input`() {
        FileTypeFilter.parseCustomExtensions("") shouldBe emptySet()
        FileTypeFilter.parseCustomExtensions("   ") shouldBe emptySet()
    }

    @Test
    fun `parseCustomExtensions lowercases`() {
        FileTypeFilter.parseCustomExtensions("APK, LOG")
            .shouldContainExactlyInAnyOrder("apk", "log")
    }
}
