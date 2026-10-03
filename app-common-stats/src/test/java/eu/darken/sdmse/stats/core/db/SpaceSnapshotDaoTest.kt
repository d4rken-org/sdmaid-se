package eu.darken.sdmse.stats.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.room.APathTypeConverter
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SpaceSnapshotDaoTest : BaseTest() {

    private lateinit var db: ReportsRoomDb
    private lateinit var dao: SpaceSnapshotDao

    private val now = Instant.parse("2026-06-01T10:00:00Z")

    private val storageA = "storage-a"
    private val storageB = "storage-b"
    private val ageDays = listOf(7L, 89L, 90L, 91L, 179L, 180L, 181L, 364L, 365L, 366L)

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReportsRoomDb::class.java)
            .addTypeConverter(APathTypeConverter(Json))
            .allowMainThreadQueries()
            .build()
        dao = db.spaceSnapshots()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun daysAgo(days: Long): Instant = now - Duration.ofDays(days)

    private suspend fun seed() {
        listOf(storageA, storageB).forEach { storageId ->
            ageDays.forEach { days ->
                dao.insert(
                    SpaceSnapshotEntity(
                        storageId = storageId,
                        recordedAt = daysAgo(days),
                        spaceFree = days,
                        spaceCapacity = 1000L,
                    ),
                )
            }
        }
    }

    /** Ascending by time, i.e. oldest (largest age) first. */
    private fun expectedWithin(days: Long): List<Instant> = ageDays
        .filter { it <= days }
        .sortedDescending()
        .map { daysAgo(it) }

    @Test
    fun `range query returns rows at or after the boundary for one storage in ascending order`() =
        runBlocking<Unit> {
            seed()

            listOf(90L, 180L, 365L).forEach { days ->
                listOf(storageA, storageB).forEach { storageId ->
                    val rows = dao.getByStorageId(storageId, daysAgo(days)).first()

                    rows.map { it.recordedAt } shouldBe expectedWithin(days)
                    rows.map { it.storageId }.distinct() shouldBe listOf(storageId)
                }
            }
        }

    @Test
    fun `deleteOlderThan removes strictly older rows across storages and keeps the boundary row`() =
        runBlocking<Unit> {
            seed()

            listOf(365L, 180L, 90L).forEach { days ->
                dao.deleteOlderThan(daysAgo(days))

                val remaining = dao.getAll(Instant.EPOCH).first()
                listOf(storageA, storageB).forEach { storageId ->
                    remaining.filter { it.storageId == storageId }.map { it.recordedAt } shouldBe
                            expectedWithin(days)
                }
                remaining.map { it.storageId }.toSet() shouldBe setOf(storageA, storageB)
            }
        }

    @Test
    fun `widening the range after a prune does not bring back deleted history`() = runBlocking<Unit> {
        seed()

        dao.deleteOlderThan(daysAgo(90))

        listOf(storageA, storageB).forEach { storageId ->
            val rows = dao.getByStorageId(storageId, daysAgo(365)).first()

            rows.map { it.recordedAt } shouldBe expectedWithin(90)
            rows.none { it.recordedAt < daysAgo(90) } shouldBe true
        }
    }
}
