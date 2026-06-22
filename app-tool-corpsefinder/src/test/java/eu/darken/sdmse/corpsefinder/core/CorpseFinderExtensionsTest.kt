package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CorpseFinderExtensionsTest : BaseTest() {

    @Test
    fun `hasData is false when Data is null`() {
        val data: CorpseFinder.Data? = null
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is false when corpses is empty`() {
        val data = CorpseFinder.Data(corpses = emptyList())
        data.hasData shouldBe false
    }

    @Test
    fun `hasData is true when corpses is non-empty`() {
        val data = CorpseFinder.Data(corpses = listOf(previewCorpse()))
        data.hasData shouldBe true
    }
}
