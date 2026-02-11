package eu.darken.sdmse.stats.core

import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.storage.StorageEnvironment
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.storage.StorageStatsManager2
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.time.Instant
import java.util.UUID

class SpaceTrackerTest : BaseTest() {

    @MockK lateinit var storageStatsManager: StorageStatsManager2
    @MockK lateinit var storageManager2: StorageManager2
    @MockK lateinit var storageEnvironment: StorageEnvironment
    @MockK lateinit var spaceHistoryRepo: SpaceHistoryRepo
    @MockK lateinit var statsSettings: StatsSettings

    private val lastSnapshotAt = mockk<eu.darken.sdmse.common.datastore.DataStoreValue<Long>>().apply {
        every { flow } returns flowOf(0L)
        coEvery { update(any()) } returns mockk()
    }

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        every { statsSettings.lastSnapshotAt } returns lastSnapshotAt
        every { storageManager2.volumes } returns null
    }

    private fun createInstance() = SpaceTracker(
        dispatcherProvider = TestDispatcherProvider(),
        storageStatsManager = storageStatsManager,
        storageManager2 = storageManager2,
        storageEnvironment = storageEnvironment,
        spaceHistoryRepo = spaceHistoryRepo,
        statsSettings = statsSettings,
    )

    @Test
    fun `recordSnapshot skipped when globally throttled`() = runTest {
        // lastSnapshotAt is recent (60s ago), within the 30min throttle window
        val recentTime = Instant.now().minusSeconds(60).toEpochMilli()
        every { lastSnapshotAt.flow } returns flowOf(recentTime)

        val tracker = createInstance()
        tracker.recordSnapshot(force = false)

        coVerify(exactly = 0) { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recordSnapshot force bypasses global throttle`() = runTest {
        val recentTime = Instant.now().minusSeconds(60).toEpochMilli()
        every { lastSnapshotAt.flow } returns flowOf(recentTime)

        coEvery { storageStatsManager.getTotalBytes(any()) } returns 100_000_000L
        coEvery { storageStatsManager.getFreeBytes(any()) } returns 50_000_000L
        coEvery { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) } returns true

        val tracker = createInstance()
        tracker.recordSnapshot(force = true)

        coVerify {
            spaceHistoryRepo.insertIfNotRecent(
                storageId = any(),
                recordedAt = any(),
                spaceFree = 50_000_000L,
                spaceCapacity = 100_000_000L,
                dedupeWindow = any(),
            )
        }
    }

    @Test
    fun `recordSnapshot with DeviceStorage maps correctly`() = runTest {
        val storageId = StorageId(internalId = null, externalId = UUID.randomUUID())
        val storage = DeviceStorage(
            id = storageId,
            label = "Primary".toCaString(),
            type = DeviceStorage.Type.PRIMARY,
            hardware = DeviceStorage.Hardware.BUILT_IN,
            spaceCapacity = 200_000_000L,
            spaceFree = 80_000_000L,
            setupIncomplete = false,
        )

        coEvery { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) } returns true

        val tracker = createInstance()
        tracker.recordSnapshot(setOf(storage))

        coVerify {
            spaceHistoryRepo.insertIfNotRecent(
                storageId = storageId.externalId.toString(),
                recordedAt = any(),
                spaceFree = 80_000_000L,
                spaceCapacity = 200_000_000L,
                dedupeWindow = any(),
            )
        }
    }

    @Test
    fun `recordSnapshot with empty storages is no-op`() = runTest {
        val tracker = createInstance()
        tracker.recordSnapshot(emptySet())

        coVerify(exactly = 0) { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recordSnapshot updates lastSnapshotAt on insert`() = runTest {
        val storageId = StorageId(internalId = null, externalId = UUID.randomUUID())
        val storage = DeviceStorage(
            id = storageId,
            label = "Test".toCaString(),
            type = DeviceStorage.Type.PRIMARY,
            hardware = DeviceStorage.Hardware.BUILT_IN,
            spaceCapacity = 100_000_000L,
            spaceFree = 50_000_000L,
            setupIncomplete = false,
        )

        coEvery { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) } returns true

        val tracker = createInstance()
        tracker.recordSnapshot(setOf(storage))

        coVerify { lastSnapshotAt.update(any()) }
    }

    @Test
    fun `recordSnapshot does not update lastSnapshotAt when nothing inserted`() = runTest {
        val storageId = StorageId(internalId = null, externalId = UUID.randomUUID())
        val storage = DeviceStorage(
            id = storageId,
            label = "Test".toCaString(),
            type = DeviceStorage.Type.PRIMARY,
            hardware = DeviceStorage.Hardware.BUILT_IN,
            spaceCapacity = 100_000_000L,
            spaceFree = 50_000_000L,
            setupIncomplete = false,
        )

        coEvery { spaceHistoryRepo.insertIfNotRecent(any(), any(), any(), any(), any()) } returns false

        val tracker = createInstance()
        tracker.recordSnapshot(setOf(storage))

        coVerify(exactly = 0) { lastSnapshotAt.update(any()) }
    }
}
