package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppCleanerExtensionsTest : BaseTest() {

    @Test
    fun `hasData is false when Data is null`() {
        val data: AppCleaner.Data? = null
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is false when junks is empty`() {
        val data = AppCleaner.Data(junks = emptyList())
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is true when junks is non-empty`() {
        val data = AppCleaner.Data(junks = listOf(previewAppJunk()))
        data.hasData shouldBe true
    }
}
