package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterStrategy
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHashBits
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.ByteString
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DeduplicatorTest : BaseTest() {
    val cksDupe1 = ChecksumDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("aaa")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
    )
    val cksDupe2 = ChecksumDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("bbb")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
    )
    val cksDupe3 = ChecksumDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("ddd")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
    )
    val phDupe2 = PHashDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("eee")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = PHasher.Result(PHashBits(2L)),
        similarity = 0.96
    )
    val phDupe1 = PHashDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("ccc")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = PHasher.Result(PHashBits(1L)),
        similarity = 0.95
    )

    private val arbiter: DuplicatesArbiter = mockk<DuplicatesArbiter>().apply {
        coEvery { getStrategy() } returns ArbiterStrategy(criteria = emptyList())
        coEvery { decideDuplicates(any(), any()) } answers {
            val dupes = firstArg<Collection<Duplicate>>().toList()
            dupes.first() to dupes.drop(1)
        }
        coEvery { decideGroups(any(), any()) } answers {
            val groups = firstArg<Collection<Duplicate.Group>>().toList()
            groups.first() to groups.drop(1).toSet()
        }
    }

    @Test
    fun `post-deletion - prune empties and singles`() = runTest {

        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("abc"),
                    groups = emptySet(),
                ),
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("123"),
                            duplicates = emptySet(),
                        ),
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("456"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("456"),
                ),
            )
        )

        original.prune(emptySet(), arbiter) shouldBe Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("456"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("456"),
                ),
            )
        )
    }

    @Test
    fun `post-deletion - prune all`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("abc"),
                    groups = emptySet(),
                ),
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("123"),
                            duplicates = emptySet(),
                        ),
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("456"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("456"),
                ),
            )
        )

        original.prune(setOf(Duplicate.Id("aaa")), arbiter) shouldBe Deduplicator.Data()
    }

    @Test
    fun `post-deletion - a cluster can have two groups with one dupe each`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("ccc"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("123"),
                            duplicates = setOf(cksDupe1),
                        ),
                        PHashDuplicate.Group(
                            identifier = Duplicate.Group.Id("456"),
                            duplicates = setOf(phDupe1),
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("123"),
                ),
            )
        )

        original.prune(emptySet(), arbiter) shouldBe original
    }

    @Test
    fun `prune recalculates keeper when keeper file is deleted`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("c1"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("g1"),
                            duplicates = setOf(cksDupe1, cksDupe2, cksDupe3),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
                ),
            )
        )

        // Delete the keeper (cksDupe1) — arbiter should pick a new one from remaining
        val result = original.prune(setOf(cksDupe1.identifier), arbiter)

        val resultGroup = result.clusters.single().groups.single()
        resultGroup.duplicates shouldBe setOf(cksDupe2, cksDupe3)
        // Arbiter mock returns first element, so new keeper should be one of the remaining
        resultGroup.keeperIdentifier shouldBe resultGroup.duplicates.first().identifier
    }

    @Test
    fun `prune preserves keeper when non-keeper file is deleted`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("c1"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("g1"),
                            duplicates = setOf(cksDupe1, cksDupe2, cksDupe3),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
                ),
            )
        )

        // Delete a non-keeper — keeper should remain unchanged
        val result = original.prune(setOf(cksDupe2.identifier), arbiter)

        val resultGroup = result.clusters.single().groups.single()
        resultGroup.duplicates shouldBe setOf(cksDupe1, cksDupe3)
        resultGroup.keeperIdentifier shouldBe cksDupe1.identifier
    }

    @Test
    fun `prune recalculates favorite group when favorite is removed`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("c1"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("g1"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                        PHashDuplicate.Group(
                            identifier = Duplicate.Group.Id("g2"),
                            duplicates = setOf(phDupe1, phDupe2),
                            keeperIdentifier = phDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
                ),
            )
        )

        // Delete both items from favorite group g1 — it gets pruned, favorite should shift to g2
        val result = original.prune(setOf(cksDupe1.identifier, cksDupe2.identifier), arbiter)

        val resultCluster = result.clusters.single()
        resultCluster.groups.single().identifier shouldBe Duplicate.Group.Id("g2")
        resultCluster.favoriteGroupIdentifier shouldBe Duplicate.Group.Id("g2")
    }

    @Test
    fun `prune preserves favorite group when non-favorite is removed`() = runTest {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("c1"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("g1"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                            keeperIdentifier = cksDupe1.identifier,
                        ),
                        PHashDuplicate.Group(
                            identifier = Duplicate.Group.Id("g2"),
                            duplicates = setOf(phDupe1, phDupe2),
                            keeperIdentifier = phDupe1.identifier,
                        ),
                    ),
                    favoriteGroupIdentifier = Duplicate.Group.Id("g1"),
                ),
            )
        )

        // Delete both items from non-favorite group g2 — favorite should stay g1
        val result = original.prune(setOf(phDupe1.identifier, phDupe2.identifier), arbiter)

        val resultCluster = result.clusters.single()
        resultCluster.groups.single().identifier shouldBe Duplicate.Group.Id("g1")
        resultCluster.favoriteGroupIdentifier shouldBe Duplicate.Group.Id("g1")
    }
}
