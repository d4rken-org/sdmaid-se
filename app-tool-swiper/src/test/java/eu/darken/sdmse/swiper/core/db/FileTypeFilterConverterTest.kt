package eu.darken.sdmse.swiper.core.db

import eu.darken.sdmse.common.serialization.SerializationIOModule
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.FileTypeFilter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FileTypeFilterConverterTest : BaseTest() {

    private lateinit var converter: FileTypeFilterConverter

    @BeforeEach
    fun setup() {
        val json = SerializationIOModule().json()
        converter = FileTypeFilterConverter(json)
    }

    @Test
    fun `null round trip`() {
        converter.from(null).shouldBeNull()
        converter.to(null).shouldBeNull()
    }

    @Test
    fun `round trip with categories`() {
        val filter = FileTypeFilter(
            categories = setOf(FileTypeCategory.IMAGES, FileTypeCategory.VIDEOS),
        )
        val json = converter.from(filter)
        val restored = converter.to(json!!)
        restored shouldBe filter
    }

    @Test
    fun `round trip with custom extensions`() {
        val filter = FileTypeFilter(
            customExtensions = setOf("apk", "log"),
        )
        val json = converter.from(filter)
        val restored = converter.to(json!!)
        restored shouldBe filter
    }

    @Test
    fun `round trip with combined filter`() {
        val filter = FileTypeFilter(
            categories = setOf(FileTypeCategory.AUDIO),
            customExtensions = setOf("wma"),
        )
        val json = converter.from(filter)
        val restored = converter.to(json!!)
        restored shouldBe filter
    }

    @Test
    fun `malformed JSON returns null`() {
        converter.to("not valid json").shouldBeNull()
        converter.to("{invalid").shouldBeNull()
    }
}
