package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DeduplicatorTest : BaseTest() {

    @Test
    fun `prune empties`() {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Identifier("abc"),
                    groups = emptySet(),
                ),
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Identifier("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Identifier("123"),
                            duplicates = emptySet(),
                        ),
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Identifier("456"),
                            duplicates = setOf(
                                ChecksumDuplicate(
                                    lookup = LocalPathLookup(
                                        lookedUp = LocalPath(File("abc")),
                                        fileType = FileType.FILE,
                                        size = 16,
                                        modifiedAt = Instant.EPOCH,
                                        target = null,
                                    ),
                                    hash = Hasher.Result(hash = ByteArray(0), type = Hasher.Type.SHA1),
                                )
                            ),
                        ),
                    )
                ),
            )
        )

        original.prune(DuplicatesDeleter.Deleted()) shouldBe Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Identifier("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Identifier("456"),
                            duplicates = setOf(
                                ChecksumDuplicate(
                                    lookup = LocalPathLookup(
                                        lookedUp = LocalPath(File("abc")),
                                        fileType = FileType.FILE,
                                        size = 16,
                                        modifiedAt = Instant.EPOCH,
                                        target = null,
                                    ),
                                    hash = Hasher.Result(hash = ByteArray(0), type = Hasher.Type.SHA1),
                                )
                            ),
                        ),
                    )
                ),
            )
        )
    }

    @Test
    fun `prune all`() {
        val original = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Identifier("abc"),
                    groups = emptySet(),
                ),
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Identifier("edf"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Identifier("123"),
                            duplicates = emptySet(),
                        ),
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Identifier("456"),
                            duplicates = setOf(
                                ChecksumDuplicate(
                                    lookup = LocalPathLookup(
                                        lookedUp = LocalPath(File("abc")),
                                        fileType = FileType.FILE,
                                        size = 16,
                                        modifiedAt = Instant.EPOCH,
                                        target = null,
                                    ),
                                    hash = Hasher.Result(hash = ByteArray(0), type = Hasher.Type.SHA1),
                                )
                            ),
                        ),
                    )
                ),
            )
        )

        original.prune(
            DuplicatesDeleter.Deleted(
                duplicates = setOf(File("abc").path)
            )
        ) shouldBe Deduplicator.Data()
    }
}