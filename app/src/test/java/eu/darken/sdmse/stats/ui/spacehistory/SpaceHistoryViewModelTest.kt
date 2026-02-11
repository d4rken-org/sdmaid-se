package eu.darken.sdmse.stats.ui.spacehistory

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.livedata.InstantExecutorExtension
import java.time.Instant

@ExtendWith(InstantExecutorExtension::class)
class SpaceHistoryViewModelTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    @MockK lateinit var storageManager2: StorageManager2
    @MockK lateinit var spaceHistoryRepo: SpaceHistoryRepo
    @MockK lateinit var upgradeRepo: UpgradeRepo

    private val upgradeInfo = mockk<UpgradeRepo.Info>().apply {
        every { isPro } returns false
    }

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)

        every { upgradeRepo.upgradeInfo } returns flowOf(upgradeInfo)
        every { storageManager2.volumes } returns emptyList()
        every { spaceHistoryRepo.getAvailableStorageIds() } returns flowOf(emptyList())
        every { spaceHistoryRepo.getHistory(any(), any()) } returns flowOf(emptyList())
        every { spaceHistoryRepo.getReports(any()) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createInstance(storageId: String? = null): SpaceHistoryViewModel {
        val handle = SavedStateHandle().apply {
            if (storageId != null) set("storageId", storageId)
        }
        return SpaceHistoryViewModel(
            handle = handle,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            spaceHistoryRepo = spaceHistoryRepo,
            upgradeRepo = upgradeRepo,
            storageManager2 = storageManager2,
        )
    }

    private fun <T> LiveData<T>.getOrAwaitValue(): T {
        var data: T? = null
        val observer = Observer<T> { data = it }
        observeForever(observer)
        removeObserver(observer)
        @Suppress("UNCHECKED_CAST")
        return data as T
    }

    @Test
    fun `default state has range DAYS_7`() = runTest {
        val vm = createInstance()
        val state = vm.state.getOrAwaitValue()

        state.selectedRange shouldBe SpaceHistoryViewModel.Range.DAYS_7
    }

    @Test
    fun `empty snapshots result in null stats`() = runTest {
        val vm = createInstance()
        val state = vm.state.getOrAwaitValue()

        state.currentUsed.shouldBeNull()
        state.minUsed.shouldBeNull()
        state.maxUsed.shouldBeNull()
        state.deltaUsed.shouldBeNull()
    }

    @Test
    fun `storages are derived from snapshot data`() = runTest {
        every { spaceHistoryRepo.getAvailableStorageIds() } returns flowOf(listOf("storage-a", "storage-b"))

        val vm = createInstance()
        val state = vm.state.getOrAwaitValue()

        state.storages.size shouldBe 2
        state.storages[0].id shouldBe "storage-a"
        state.storages[1].id shouldBe "storage-b"
    }

    @Test
    fun `storageId from nav arg is selected`() = runTest {
        every { spaceHistoryRepo.getAvailableStorageIds() } returns flowOf(listOf("storage-a", "storage-b"))
        every { spaceHistoryRepo.getHistory(eq("storage-b"), any()) } returns flowOf(emptyList())

        val vm = createInstance(storageId = "storage-b")
        val state = vm.state.getOrAwaitValue()

        state.selectedStorageId shouldBe "storage-b"
    }

    @Test
    fun `delta is last minus first spaceUsed`() = runTest {
        val now = Instant.now()
        val snapshots = listOf(
            SpaceSnapshotEntity(
                storageId = "primary",
                recordedAt = now.minusSeconds(3600),
                spaceFree = 1_000_000L,
                spaceCapacity = 10_000_000L,
            ),
            SpaceSnapshotEntity(
                storageId = "primary",
                recordedAt = now,
                spaceFree = 1_500_000L,
                spaceCapacity = 10_000_000L,
            ),
        )

        every { spaceHistoryRepo.getAvailableStorageIds() } returns flowOf(listOf("primary"))
        every { spaceHistoryRepo.getHistory(eq("primary"), any()) } returns flowOf(snapshots)

        val vm = createInstance()
        val state = vm.state.getOrAwaitValue()

        // first used = 10M - 1M = 9M, last used = 10M - 1.5M = 8.5M
        state.deltaUsed shouldBe -500_000L
        state.currentUsed shouldBe 8_500_000L
        state.minUsed shouldBe 8_500_000L
        state.maxUsed shouldBe 9_000_000L
    }

    @Test
    fun `non-pro shows upgrade prompt`() = runTest {
        val vm = createInstance()
        val state = vm.state.getOrAwaitValue()

        state.isPro shouldBe false
        state.showUpgradePrompt shouldBe true
    }

    @Test
    fun `pro user can select extended ranges`() = runTest {
        every { upgradeInfo.isPro } returns true

        every { spaceHistoryRepo.getAvailableStorageIds() } returns flowOf(listOf("primary"))
        every { spaceHistoryRepo.getHistory(any(), any()) } returns flowOf(emptyList())

        val vm = createInstance()
        vm.selectRange(SpaceHistoryViewModel.Range.DAYS_30)
        advanceUntilIdle()

        val state = vm.state.getOrAwaitValue()
        state.selectedRange shouldBe SpaceHistoryViewModel.Range.DAYS_30
        state.isPro shouldBe true
    }
}
