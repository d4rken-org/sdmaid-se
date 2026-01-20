package eu.darken.sdmse.deduplicator.core

import eu.darken.sdmse.common.collections.toByteString
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.deduplicator.core.arbiter.ArbiterStrategy
import eu.darken.sdmse.deduplicator.core.arbiter.DuplicatesArbiter
import eu.darken.sdmse.deduplicator.core.deleter.DuplicatesDeleter
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DuplicatesDeleterTest : BaseTest() {

    private val gatewaySwitch: GatewaySwitch = mockk<GatewaySwitch>().apply {
        coEvery { delete(any(), any()) } returns Unit
    }
    private val arbiter: DuplicatesArbiter = mockk<DuplicatesArbiter>().apply {
        coEvery { getStrategy() } returns ArbiterStrategy(criteria = emptyList())
        coEvery { decideGroups(any(), any()) } answers {
            val groups = arg<Collection<Duplicate.Group>>(0)
            groups.first() to groups.drop(1).toSet()
        }
        coEvery { decideDuplicates(any(), any()) } answers {
            val dupes = arg<Collection<Duplicate>>(0).sortedBy { it.path.path }
            dupes.first() to dupes.drop(1).toSet()
        }
    }

    fun create() = DuplicatesDeleter(
        gatewaySwitch = gatewaySwitch,
        arbiter = arbiter
    )

    @Test
    fun `delete all flag`() = runTest {
        val deleter = create()

        val dupe1 = ChecksumDuplicate(
            lookup = mockk<APathLookup<*>>().apply {
                every { lookedUp } returns LocalPath.build("path", "dupe1")
                every { path } returns lookedUp.path
                every { userReadablePath } returns lookedUp.userReadablePath
            },
            hash = Hasher.Result(
                type = Hasher.Type.MD5,
                hash = "hash1".toByteString(),
            )
        )
        val dupe2 = ChecksumDuplicate(
            lookup = mockk<APathLookup<*>>().apply {
                every { lookedUp } returns LocalPath.build("path", "dupe2")
                every { path } returns lookedUp.path
                every { userReadablePath } returns lookedUp.userReadablePath
            },
            hash = Hasher.Result(
                type = Hasher.Type.MD5,
                hash = "hash2".toByteString(),
            )
        )
        val data = Deduplicator.Data(
            clusters = setOf(
                Duplicate.Cluster(
                    identifier = Duplicate.Cluster.Id("cluster1"),
                    groups = setOf(
                        ChecksumDuplicate.Group(
                            identifier = Duplicate.Group.Id("group1"),
                            duplicates = setOf(dupe1, dupe2),
                        ),
                    )
                )
            )
        )

        deleter.delete(
            task = DeduplicatorDeleteTask(),
            data = data,
        ).success shouldBe setOf(dupe2)

        deleter.delete(
            task = DeduplicatorDeleteTask(
                mode = DeduplicatorDeleteTask.TargetMode.Groups(
                    deleteAll = false,
                    setOf(Duplicate.Group.Id("group1"))
                )
            ),
            data = data,
        ).success shouldBe setOf(dupe2)

        deleter.delete(
            task = DeduplicatorDeleteTask(
                mode = DeduplicatorDeleteTask.TargetMode.Groups(
                    deleteAll = true,
                    setOf(Duplicate.Group.Id("group1"))
                )
            ),
            data = data,
        ).success shouldBe setOf(dupe1, dupe2)
    }
}