package eu.darken.sdmse.analyzer.core.content

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.local.LocalPath
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ContentGroupTest : BaseTest() {

    @Test
    fun `groupSize sums content sizes`() {
        val group = ContentGroup(
            label = "test".toCaString(),
            contents = setOf(
                ContentItem.fromInaccessible(LocalPath.build("a"), 100L),
                ContentItem.fromInaccessible(LocalPath.build("b"), 200L),
            ),
        )
        group.groupSize shouldBe 300L
    }

    @Test
    fun `groupSize treats null sizes as zero`() {
        val group = ContentGroup(
            label = "test".toCaString(),
            contents = setOf(
                ContentItem.fromInaccessible(LocalPath.build("a"), 100L),
                ContentItem.fromInaccessible(LocalPath.build("b")),
            ),
        )
        group.groupSize shouldBe 100L
    }

    @Test
    fun `groupSize is zero for empty contents`() {
        val group = ContentGroup(
            label = "test".toCaString(),
            contents = emptySet(),
        )
        group.groupSize shouldBe 0L
    }
}
