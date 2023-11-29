package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.PHashDuplicate
import eu.darken.sdmse.deduplicator.core.scanner.phash.phash.PHasher
import io.kotest.matchers.shouldBe
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
    val phDupe1 = PHashDuplicate(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File("ccc")),
            fileType = FileType.FILE,
            size = 16,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        hash = PHasher.Result(1L),
        similarity = 0.95
    )

    @Test
    fun `post-deletion - prune empties and singles`() {

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
                        ),
                    )
                ),
            )
        )

        original.prune(emptySet()) shouldBe Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("456"),
                            duplicates = setOf(cksDupe1, cksDupe2),
                        ),
                    )
                ),
            )
        )
    }

    @Test
    fun `post-deletion - prune all`() {
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
                        ),
                    )
                ),
            )
        )

        original.prune(setOf(Duplicate.Id("aaa"))) shouldBe Deduplicator.Data()
    }

    @Test
    fun `post-deletion - a cluster can have two groups with one dupe each`() {
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
                    )
                ),
            )
        )

        original.prune(emptySet()) shouldBe original
    }
}