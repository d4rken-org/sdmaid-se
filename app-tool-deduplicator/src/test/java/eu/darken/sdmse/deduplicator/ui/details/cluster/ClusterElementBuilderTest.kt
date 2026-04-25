package eu.darken.sdmse.deduplicator.ui.details.cluster

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ClusterElementBuilderTest : BaseTest() {

    private fun mockChecksum(id: String, parent: List<String> = listOf("storage"), name: String = id): ChecksumDuplicate {
        val segments = parent + name
        val mockPath = mockk<APath> {
            every { this@mockk.segments } returns segments
            every { this@mockk.name } returns name
        }
        val mockLookup = mockk<APathLookup<*>> {
            every { lookedUp } returns mockPath
            every { this@mockk.segments } returns segments
            every { size } returns 100L
        }
        return mockk {
            every { lookup } returns mockLookup
            every { identifier } returns Duplicate.Id(id)
            every { this@mockk.path } returns mockPath
            every { type } returns Duplicate.Type.CHECKSUM
            every { size } returns 100L
        }
    }

    private fun mockPHash(id: String, parent: List<String> = listOf("storage"), name: String = id): PHashDuplicate {
        val segments = parent + name
        val mockPath = mockk<APath> {
            every { this@mockk.segments } returns segments
            every { this@mockk.name } returns name
        }
        val mockLookup = mockk<APathLookup<*>> {
            every { lookedUp } returns mockPath
            every { this@mockk.segments } returns segments
            every { size } returns 200L
        }
        return mockk {
            every { lookup } returns mockLookup
            every { identifier } returns Duplicate.Id(id)
            every { this@mockk.path } returns mockPath
            every { type } returns Duplicate.Type.PHASH
            every { size } returns 200L
        }
    }

    @Test
    fun `group view - single group emits header and file rows`() {
        val a = mockChecksum("a")
        val b = mockChecksum("b")
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(a, b),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
            favoriteGroupIdentifier = null,
        )

        val elements = buildClusterElements(cluster, isDirectoryView = false, collapsed = emptySet())

        elements.size shouldBe 4 // cluster header + group header + 2 dupe rows
        elements[0].shouldBeInstanceOf<ClusterElement.ClusterHeader>()
        val groupHeader = elements[1].shouldBeInstanceOf<ClusterElement.ChecksumGroupHeader>()
        groupHeader.willBeDeleted shouldBe false // single group, no favorite logic applies
        val rowA = elements[2].shouldBeInstanceOf<ClusterElement.ChecksumDuplicateRow>()
        rowA.willBeDeleted shouldBe false // keeper
        val rowB = elements[3].shouldBeInstanceOf<ClusterElement.ChecksumDuplicateRow>()
        rowB.willBeDeleted shouldBe true // non-keeper
    }

    @Test
    fun `group view - non-favorite group is marked willBeDeleted when favorite exists`() {
        val a = mockChecksum("a")
        val favGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("favourite"),
            duplicates = setOf(a),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val c = mockPHash("c")
        val d = mockPHash("d")
        val nonFavGroup = PHashDuplicate.Group(
            identifier = Duplicate.Group.Id("other"),
            duplicates = setOf(c, d),
            keeperIdentifier = null,
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(favGroup, nonFavGroup),
            favoriteGroupIdentifier = Duplicate.Group.Id("favourite"),
        )

        val elements = buildClusterElements(cluster, isDirectoryView = false, collapsed = emptySet())

        val favHeader = elements.filterIsInstance<ClusterElement.ChecksumGroupHeader>().single()
        favHeader.willBeDeleted shouldBe false
        val nonFavHeader = elements.filterIsInstance<ClusterElement.PHashGroupHeader>().single()
        nonFavHeader.willBeDeleted shouldBe true
    }

    @Test
    fun `group view - groups sorted by totalSize descending`() {
        val small = mockChecksum("small")
        val smallGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("small"),
            duplicates = setOf(small),
            keeperIdentifier = null,
        )
        val big1 = mockChecksum("big1")
        val big2 = mockChecksum("big2")
        val bigGroup = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("big"),
            duplicates = setOf(big1, big2),
            keeperIdentifier = null,
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(smallGroup, bigGroup),
        )

        val elements = buildClusterElements(cluster, isDirectoryView = false, collapsed = emptySet())

        val groupHeaders = elements.filterIsInstance<ClusterElement.ChecksumGroupHeader>()
        groupHeaders[0].group.identifier.value shouldBe "big"
        groupHeaders[1].group.identifier.value shouldBe "small"
    }

    @Test
    fun `directory view - collapsed directory emits only header`() {
        val a = mockChecksum("a", parent = listOf("storage", "Pictures"))
        val b = mockChecksum("b", parent = listOf("storage", "Pictures"))
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(a, b),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
        )

        val dirHeader = buildClusterElements(cluster, isDirectoryView = true, collapsed = emptySet())
            .filterIsInstance<ClusterElement.DirectoryHeader>()
            .single()
        val collapsedId = dirHeader.group.identifier

        val elements = buildClusterElements(
            cluster,
            isDirectoryView = true,
            collapsed = setOf(collapsedId),
        )

        elements.filterIsInstance<ClusterElement.DirectoryHeader>().single().isCollapsed shouldBe true
        elements.filterIsInstance<ClusterElement.DuplicateRow>().shouldBeEmpty()
    }

    @Test
    fun `directory view - non-collapsed emits header plus file rows ordered by name`() {
        val z = mockChecksum("z", parent = listOf("storage", "Pictures"), name = "z.jpg")
        val a = mockChecksum("a", parent = listOf("storage", "Pictures"), name = "a.jpg")
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(z, a),
            keeperIdentifier = Duplicate.Id("a"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
        )

        val elements = buildClusterElements(cluster, isDirectoryView = true, collapsed = emptySet())

        val rows = elements.filterIsInstance<ClusterElement.ChecksumDuplicateRow>()
        rows[0].duplicate.identifier.value shouldBe "a"
        rows[1].duplicate.identifier.value shouldBe "z"
    }

    @Test
    fun `directory view - willBeDeleted matches non-keeper duplicates`() {
        val keeper = mockChecksum("k", parent = listOf("storage"))
        val nonKeeper = mockChecksum("nk", parent = listOf("storage"))
        val group = ChecksumDuplicate.Group(
            identifier = Duplicate.Group.Id("g1"),
            duplicates = setOf(keeper, nonKeeper),
            keeperIdentifier = Duplicate.Id("k"),
        )
        val cluster = Duplicate.Cluster(
            identifier = Duplicate.Cluster.Id("c1"),
            groups = setOf(group),
        )

        val elements = buildClusterElements(cluster, isDirectoryView = true, collapsed = emptySet())

        val rowK = elements.filterIsInstance<ClusterElement.ChecksumDuplicateRow>()
            .single { it.duplicate.identifier.value == "k" }
        val rowNk = elements.filterIsInstance<ClusterElement.ChecksumDuplicateRow>()
            .single { it.duplicate.identifier.value == "nk" }
        rowK.willBeDeleted shouldBe false
        rowNk.willBeDeleted shouldBe true
    }
}
