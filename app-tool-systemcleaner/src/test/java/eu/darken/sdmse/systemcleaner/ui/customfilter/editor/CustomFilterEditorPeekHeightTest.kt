package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CustomFilterEditorPeekHeightTest : BaseTest() {

    @Test
    fun `peek height covers drag handle, summary and navigation bar`() {
        liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 48.dp,
        ) shouldBe 152.dp
    }

    @Test
    fun `navigation bar inset is part of the peek band`() {
        val withoutInset = liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 0.dp,
        )
        val withInset = liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 24.dp,
        )

        (withInset - withoutInset) shouldBe 24.dp
    }

    @Test
    fun `drag handle height is part of the peek band`() {
        val small = liveSearchPeekHeight(
            dragHandleHeight = 0.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 24.dp,
        )
        val large = liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 24.dp,
        )

        (large - small) shouldBe 48.dp
    }

    @Test
    fun `match row hint is only added when there are matches`() {
        val noMatches = liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = false,
            navigationBarBottom = 24.dp,
            matchRowPeek = 24.dp,
        )
        val withMatches = liveSearchPeekHeight(
            dragHandleHeight = 48.dp,
            summaryHeight = 56.dp,
            hasMatches = true,
            navigationBarBottom = 24.dp,
            matchRowPeek = 24.dp,
        )

        noMatches shouldBe 128.dp
        withMatches shouldBe 152.dp
    }
}
