package eu.darken.sdmse.systemcleaner.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SystemCleanerExtensionsTest : BaseTest() {

    @Test
    fun `hasData is false when Data is null`() {
        val data: SystemCleaner.Data? = null
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is false when filterContents is empty`() {
        val data = SystemCleaner.Data(filterContents = emptyList())
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is true when filterContents is non-empty`() {
        val data = SystemCleaner.Data(filterContents = listOf(fakeFilterContent()))
        data.hasData shouldBe true
    }
}
