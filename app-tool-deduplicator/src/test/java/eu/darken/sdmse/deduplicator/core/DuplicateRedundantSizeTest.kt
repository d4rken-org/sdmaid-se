package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicateRedundantSizeTest : BaseTest() {

    private fun mockChecksumDupe(id: String, size: Long): ChecksumDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
        every { this@mockk.size } returns size
    }

    private fun mockPhashDupe(id: String, size: Long): PHashDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
        every { this@mockk.size } returns size
    }

    @Test
    fun `group - same size files with keeper`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(
                mockChecksumDupe("a", 100L),
                mockChecksumDupe("b", 100L),
                mockChecksumDupe("c", 100L),
            ),
            keeperIdentifier = Duplicate.Id("a"),
        )
        group.redundantSize shouldBe 200L
    }

    @Test
    fun `group - different size files with keeper picks correct keeper`() {
        val group = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(
                mockPhashDupe("a", 500L),
                mockPhashDupe("b", 300L),
                mockPhashDupe("c", 200L),
            ),
            keeperIdentifier = Duplicate.Id("b"),
        )
        // Keeper is b (300), redundant = 500 + 200 = 700
        group.redundantSize shouldBe 700L
    }

    @Test
    fun `group - null keeper falls back to first element`() {
        val d1 = mockPhashDupe("a", 500L)
        val d2 = mockPhashDupe("b", 300L)
        val group = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(d1, d2),
            keeperIdentifier = null,
        )
        val firstSize = group.duplicates.first().size
        group.redundantSize shouldBe group.totalSize - firstSize
    }

    @Test
    fun `group - single duplicate has zero redundant size`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a", 100L)),
            keeperIdentifier = Duplicate.Id("a"),
        )
        group.redundantSize shouldBe 0L
    }

    @Test
    fun `group - keeper not found falls back to first element`() {
        val d1 = mockPhashDupe("a", 500L)
        val d2 = mockPhashDupe("b", 300L)
        val group = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(d1, d2),
            keeperIdentifier = Duplicate.Id("nonexistent"),
        )
        val firstSize = group.duplicates.first().size
        group.redundantSize shouldBe group.totalSize - firstSize
    }

    @Test
    fun `cluster - single group with favorite`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(
                mockChecksumDupe("a", 100L),
                mockChecksumDupe("b", 100L),
            ),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        cluster.redundantSize shouldBe 100L
    }

    @Test
    fun `cluster - multi group, favorite uses redundantSize, rest uses totalSize`() {
        val favGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(
                mockChecksumDupe("a", 100L),
                mockChecksumDupe("b", 100L),
            ),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val nonFavGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(
                mockPhashDupe("c", 200L),
                mockPhashDupe("d", 300L),
            ),
            keeperIdentifier = Duplicate.Id("c"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(favGroup, nonFavGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Favorite: redundantSize = 100 (keeps a=100, deletes b=100)
        // Non-favorite: totalSize = 500 (deletes both c=200 and d=300)
        cluster.redundantSize shouldBe 600L
    }

    @Test
    fun `cluster - favorite group with 1 member uses totalSize`() {
        val singleGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a", 150L)),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val multiGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(
                mockPhashDupe("b", 200L),
                mockPhashDupe("c", 300L),
            ),
            keeperIdentifier = Duplicate.Id("b"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(singleGroup, multiGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Favorite (1 member): totalSize = 150 (deleter deletes sole file)
        // Non-favorite: totalSize = 500
        cluster.redundantSize shouldBe 650L
    }

    @Test
    fun `cluster - null favorite treats all groups as fully deletable`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(
                mockChecksumDupe("a", 100L),
                mockChecksumDupe("b", 100L),
            ),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = null,
        )
        // No favorite: totalSize for all groups = 200
        cluster.redundantSize shouldBe 200L
    }
}
