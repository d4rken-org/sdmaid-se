package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * [Duplicate.Cluster.redundantCount] is the count counterpart of [Duplicate.Cluster.redundantSize]:
 * the number of files a default keep-one delete removes. Fixtures mirror [DuplicateRedundantSizeTest]
 * so size and count stay in lockstep. "Normalized" fixtures match what the scanner produces (every
 * group with >= 2 members has a keeper, every cluster has a favorite); cases noted "defensive" can
 * never reach the hero from a real scan but the property must still not crash or over-count.
 */
class DuplicateRedundantCountTest : BaseTest() {

    private fun mockChecksumDupe(id: String, size: Long = 1L): ChecksumDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
        every { this@mockk.size } returns size
    }

    private fun mockPhashDupe(id: String, size: Long = 1L): PHashDuplicate = mockk {
        every { identifier } returns Duplicate.Id(id)
        every { this@mockk.size } returns size
    }

    @Test
    fun `cluster - single group with favorite keeps one`() {
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Keeps a, removes b.
        cluster.redundantCount shouldBe 1
    }

    @Test
    fun `cluster - multi group, favorite keeps one, rest fully removed`() {
        val favGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val nonFavGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("c"), mockPhashDupe("d")),
            keeperIdentifier = Duplicate.Id("c"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(favGroup, nonFavGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Favorite: removes b (1). Non-favorite: removes c and d (2).
        cluster.redundantCount shouldBe 3
    }

    @Test
    fun `cluster - favorite group with 1 member removes the sole file`() {
        val singleGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val multiGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("b"), mockPhashDupe("c")),
            keeperIdentifier = Duplicate.Id("b"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(singleGroup, multiGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Favorite has <2 members so the keep-one rule doesn't apply: its sole file is removed (1).
        // Non-favorite: removes b and c (2). Mirrors the redundantSize totalSize branch.
        cluster.redundantCount shouldBe 3
    }

    @Test
    fun `cluster - null favorite treats every duplicate as removable (defensive)`() {
        // The scanner always assigns a favorite; null only happens for pre-scan data that never
        // reaches the hero. The count must still mirror redundantSize's "all groups fully removed".
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("b")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = null,
        )
        cluster.redundantCount shouldBe 2
    }

    @Test
    fun `cluster - a path shared across groups is counted once (defensive)`() {
        // stripCoveredPaths normally prevents a path from surviving in two groups of one cluster;
        // distinct-by-id is a guard so the count can never exceed the deleter's distinct deletions.
        val favGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(mockChecksumDupe("a"), mockChecksumDupe("shared")),
            keeperIdentifier = Duplicate.Id("a"),
        )
        // "shared" reappears here as a different concrete duplicate but the same identifier/path.
        val nonFavGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("g2"),
            duplicates = setOf(mockPhashDupe("shared"), mockPhashDupe("c")),
            keeperIdentifier = Duplicate.Id("shared"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(favGroup, nonFavGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
        )
        // Favorite removes {shared}; non-favorite removes {shared, c}. Distinct union = {shared, c} = 2.
        // A naive per-group sum would wrongly report 3.
        cluster.redundantCount shouldBe 2
    }
}
