package eu.darken.sdmse.stats.core

import eu.darken.sdmse.stats.core.db.ReportsDatabase
import eu.darken.sdmse.stats.core.db.SpaceSnapshotDao
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

class SpaceHistoryRepoTest : BaseTest() {

    @MockK lateinit var reportsDatabase: ReportsDatabase
    @MockK lateinit var spaceSnapshotDao: SpaceSnapshotDao

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { reportsDatabase.spaceSnapshotDao } returns spaceSnapshotDao
        every { reportsDatabase.refreshDatabaseSize() } returns Unit
        coEvery { reportsDatabase.withTransaction<Any?>(any()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }

        coEvery { spaceSnapshotDao.insert(any()) } returns Unit
        coEvery { spaceSnapshotDao.deleteById(any()) } returns Unit
    }

    @Test
    fun `insertIfNotRecent replaces latest snapshot within dedupe window`() = runTest {
        val now = Instant.now()
        coEvery { spaceSnapshotDao.getLatest("primary") } returns SpaceSnapshotEntity(
            id = 7,
            storageId = "primary",
            recordedAt = now.minusSeconds(60),
            spaceFree = 10L,
            spaceCapacity = 100L,
        )

        val repo = SpaceHistoryRepo(reportsDatabase)
        repo.insertIfNotRecent(
            storageId = "primary",
            recordedAt = now,
            spaceFree = 20L,
            spaceCapacity = 100L,
            dedupeWindow = Duration.ofMinutes(5),
        )

        coVerify(exactly = 1) { spaceSnapshotDao.deleteById(7) }
        coVerify(exactly = 1) { spaceSnapshotDao.insert(any()) }
    }

    @Test
    fun `insertIfNotRecent keeps latest snapshot when outside dedupe window`() = runTest {
        val now = Instant.now()
        coEvery { spaceSnapshotDao.getLatest("primary") } returns SpaceSnapshotEntity(
            id = 8,
            storageId = "primary",
            recordedAt = now.minus(Duration.ofMinutes(10)),
            spaceFree = 10L,
            spaceCapacity = 100L,
        )

        val repo = SpaceHistoryRepo(reportsDatabase)
        repo.insertIfNotRecent(
            storageId = "primary",
            recordedAt = now,
            spaceFree = 20L,
            spaceCapacity = 100L,
            dedupeWindow = Duration.ofMinutes(5),
        )

        coVerify(exactly = 0) { spaceSnapshotDao.deleteById(any()) }
        coVerify(exactly = 1) { spaceSnapshotDao.insert(any()) }
    }
}
