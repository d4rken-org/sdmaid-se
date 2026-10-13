package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicateKeepOneOfTest : BaseTest() {

    private fun mockChecksumDupe(id: String): ChecksumDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
    }

    private fun mockPhashDupe(id: String): PHashDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
    }

    private fun ids(vararg values: String) = values.map { Duplicate.Id(it) }.toSet()

    @Test
    fun `whole set targeted - favorite keeper is left out`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b"), mockChecksumDupe("c")),
            keeperIdentifier = Duplicate.Id("b"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )

        cluster.keptDuplicateId shouldBe Duplicate.Id("b")
        cluster.keepOneOf(ids("a", "b", "c")) shouldBe ids("a", "c")
    }

    @Test
    fun `multi group - every id targeted leaves out only the favorite keeper`() {
        val nonFavGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val favGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("c"), mockPhashDupe("d")),
            keeperIdentifier = Duplicate.Id("d"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(nonFavGroup, favGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g2"),
        )

        cluster.keptDuplicateId shouldBe Duplicate.Id("d")
        cluster.keepOneOf(ids("a", "b", "c", "d")) shouldBe ids("a", "b", "c")
    }

    @Test
    fun `all but one file targeted - returned unchanged`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b"), mockChecksumDupe("c")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )

        // c survives outside the folder, so the keeper a may go.
        cluster.keepOneOf(ids("a", "b")) shouldBe ids("a", "b")
    }

    @Test
    fun `single member favorite group - sole member is kept`() {
        val favGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a")),
            keeperIdentifier = null,
        )
        val otherGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("b"), mockPhashDupe("c")),
            keeperIdentifier = Duplicate.Id("b"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(favGroup, otherGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )

        cluster.keptDuplicateId shouldBe Duplicate.Id("a")
        cluster.keepOneOf(ids("a", "b", "c")) shouldBe ids("b", "c")
    }

    @Test
    fun `ids outside the cluster are dropped`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b"), mockChecksumDupe("c")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )

        cluster.keepOneOf(ids("b", "x")) shouldBe ids("b")
        cluster.keepOneOf(ids("a", "b", "c", "x")) shouldBe ids("b", "c")
    }

    @Test
    fun `null favorite falls back to the first live id (defensive)`() {
        val group1 = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b")),
            keeperIdentifier = Duplicate.Id("b"),
        )
        val group2 = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("c"), mockPhashDupe("d")),
            keeperIdentifier = Duplicate.Id("c"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group1, group2),
            favoriteGroupIdentifier = null,
        )

        cluster.keptDuplicateId shouldBe null
        // First in the cluster's group/duplicate order, not in the targets' order.
        cluster.keepOneOf(ids("d", "c", "b", "a")) shouldBe ids("b", "c", "d")
    }
}
