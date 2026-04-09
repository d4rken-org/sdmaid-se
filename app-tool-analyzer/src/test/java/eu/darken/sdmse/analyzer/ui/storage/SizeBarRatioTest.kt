package eu.darken.sdmse.analyzer.ui.storage

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SizeBarRatioTest : BaseTest() {

    @Test
    fun `ratio is itemSize divided by maxSiblingSize`() {
        val ratio = computeSizeBarRatio(itemSize = 30L, maxSiblingSize = 54L)
        ratio!! shouldBe (30f / 54f).plusOrMinus(0.0001f)
    }

    @Test
    fun `largest sibling yields ratio of exactly one`() {
        computeSizeBarRatio(itemSize = 54L, maxSiblingSize = 54L) shouldBe 1f
    }

    @Test
    fun `null itemSize yields null ratio`() {
        computeSizeBarRatio(itemSize = null, maxSiblingSize = 54L).shouldBeNull()
    }

    @Test
    fun `null maxSiblingSize yields null ratio`() {
        computeSizeBarRatio(itemSize = 30L, maxSiblingSize = null).shouldBeNull()
    }

    @Test
    fun `zero maxSiblingSize yields null ratio to avoid divide by zero`() {
        computeSizeBarRatio(itemSize = 30L, maxSiblingSize = 0L).shouldBeNull()
    }

    @Test
    fun `negative maxSiblingSize yields null ratio`() {
        computeSizeBarRatio(itemSize = 30L, maxSiblingSize = -1L).shouldBeNull()
    }

    @Test
    fun `itemSize greater than max is clamped to one`() {
        computeSizeBarRatio(itemSize = 100L, maxSiblingSize = 50L) shouldBe 1f
    }

    @Test
    fun `zero itemSize yields zero ratio`() {
        computeSizeBarRatio(itemSize = 0L, maxSiblingSize = 54L) shouldBe 0f
    }
}
