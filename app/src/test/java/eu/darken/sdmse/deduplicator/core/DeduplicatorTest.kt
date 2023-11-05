package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import io.kotest.matchers.shouldBe
import okio.ByteString
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DeduplicatorTest : BaseTest() {

    @Test
    fun `post-deletion - prune empties and singles`() {

        val dupe1 = ChecksumDuplicate(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("aaa")),
                fileType = FileType.FILE,
                size = 16,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
        )
        val dupe2 = ChecksumDuplicate(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("bbb")),
                fileType = FileType.FILE,
                size = 16,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
        )
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
                            duplicates = setOf(dupe1, dupe2),
                        ),
                    )
                ),
            )
        )

        val pruneResult = PruneResult(
            newData = Deduplicator.Data(
                clusters = setOf(
                    Duplicate.Cluster(
                        identifier = Duplicate.Cluster.Id("edf"),
                        groups = setOf(
                            ChecksumDuplicate.Group(
                                identifier = Duplicate.Group.Id("456"),
                                duplicates = setOf(dupe1, dupe2),
                            ),
                        )
                    ),
                )
            ),
            freed = 0L,
            removed = emptySet()
        )

        original.prune(DuplicatesDeleter.Deleted()) shouldBe pruneResult
    }

    @Test
    fun `post-deletion - prune all`() {
        val dupe1 = ChecksumDuplicate(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("aaa")),
                fileType = FileType.FILE,
                size = 16,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
        )
        val dupe2 = ChecksumDuplicate(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File("bbb")),
                fileType = FileType.FILE,
                size = 16,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            hash = Hasher.Result(hash = ByteString.Companion.EMPTY, type = Hasher.Type.SHA1),
        )
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
                            duplicates = setOf(dupe1, dupe2),
                        ),
                    )
                ),
            )
        )

        val pruneResult = PruneResult(
            newData = Deduplicator.Data(),
            freed = setOf(dupe1).sumOf { it.size },
            removed = setOf(dupe1)
        )

        original.prune(
            DuplicatesDeleter.Deleted(
                success = setOf(Duplicate.Id("aaa")),
            )
        ) shouldBe pruneResult
    }
}