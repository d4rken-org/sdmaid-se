package eu.darken.sdmse.stats.ui.spacehistory

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.ui.spacehistory.SpaceHistoryViewModel.Range
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.Instant

class SpaceHistoryViewModelTest : BaseTest() {

    @Test
    fun `visible ranges follow the retention`() {
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(7)) shouldBe listOf(Range.DAYS_7)
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(89)) shouldBe listOf(Range.DAYS_7, Range.DAYS_30)
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(90)) shouldBe listOf(
            Range.DAYS_7,
            Range.DAYS_30,
            Range.DAYS_90,
        )
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(179)) shouldBe listOf(
            Range.DAYS_7,
            Range.DAYS_30,
            Range.DAYS_90,
        )
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(180)) shouldBe listOf(
            Range.DAYS_7,
            Range.DAYS_30,
            Range.DAYS_90,
            Range.DAYS_180,
        )
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(364)) shouldBe listOf(
            Range.DAYS_7,
            Range.DAYS_30,
            Range.DAYS_90,
            Range.DAYS_180,
        )
        SpaceHistoryViewModel.visibleRanges(Duration.ofDays(365)) shouldBe Range.entries
    }

    @Test
    fun `Pro keeps a selection that fits the retention`() {
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_365, Duration.ofDays(365), isPro = true) shouldBe Range.DAYS_365
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_180, Duration.ofDays(200), isPro = true) shouldBe Range.DAYS_180
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_30, Duration.ofDays(90), isPro = true) shouldBe Range.DAYS_30
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_7, Duration.ofDays(7), isPro = true) shouldBe Range.DAYS_7
    }

    @Test
    fun `Pro falls back to the longest visible range when the selection no longer fits`() {
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_365, Duration.ofDays(179), isPro = true) shouldBe Range.DAYS_90
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_365, Duration.ofDays(7), isPro = true) shouldBe Range.DAYS_7
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_180, Duration.ofDays(364), isPro = true) shouldBe Range.DAYS_180
        SpaceHistoryViewModel.effectiveRange(Range.DAYS_90, Duration.ofDays(30), isPro = true) shouldBe Range.DAYS_30
    }

    @Test
    fun `free users always get 7 days`() {
        Range.entries.forEach { requested ->
            SpaceHistoryViewModel.effectiveRange(requested, Duration.ofDays(365), isPro = false) shouldBe Range.DAYS_7
            SpaceHistoryViewModel.effectiveRange(requested, Duration.ofDays(90), isPro = false) shouldBe Range.DAYS_7
        }
    }

    private fun info(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { isSettled } returns true
    }

    private class Harness(
        val vm: SpaceHistoryViewModel,
        val retention: MutableStateFlow<Duration>,
        val upgradeInfo: MutableStateFlow<UpgradeRepo.Info>,
        val historySince: List<Instant>,
        val reportSince: List<Instant>,
    )

    private fun TestScope.harness(
        isPro: Boolean,
        retention: Duration,
    ): Harness {
        val retentionFlow = MutableStateFlow(retention)
        val upgradeInfo = MutableStateFlow(info(isPro))
        val historySince = mutableListOf<Instant>()
        val reportSince = mutableListOf<Instant>()

        val spaceHistoryRepo = mockk<SpaceHistoryRepo>().apply {
            every { getAvailableStorageIds() } returns flowOf(listOf(STORAGE_ID))
            every { getHistory(any(), any()) } answers {
                historySince += secondArg<Instant>()
                flowOf(emptyList())
            }
            every { getReports(any()) } answers {
                reportSince += firstArg<Instant>()
                flowOf(emptyList())
            }
        }
        val statsSettings = mockk<StatsSettings>().apply {
            every { retentionSnapshots } returns mockk<DataStoreValue<Duration>>().apply {
                every { flow } returns retentionFlow
            }
        }
        val vm = SpaceHistoryViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            spaceHistoryRepo = spaceHistoryRepo,
            upgradeRepo = mockk<UpgradeRepo>().apply { every { this@apply.upgradeInfo } returns upgradeInfo },
            storageManager2 = mockk<StorageManager2>(relaxed = true).apply { every { volumes } returns emptyList() },
            statsSettings = statsSettings,
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, retentionFlow, upgradeInfo, historySince, reportSince)
    }

    private fun Instant.shouldBeAbout(expected: Instant) {
        Duration.between(this, expected).abs() shouldBeLessThan Duration.ofSeconds(5)
    }

    private fun Harness.sinceShouldMatch(range: Range) {
        val expected = Instant.now() - range.retention
        historySince.last().shouldBeAbout(expected)
        reportSince.last().shouldBeAbout(expected)
    }

    @Test
    fun `Pro with a one year retention can view one year`() = runTest2 {
        val h = harness(isPro = true, retention = Duration.ofDays(365))
        advanceUntilIdle()

        h.vm.selectRange(Range.DAYS_365)
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.selectedStorageId shouldBe STORAGE_ID
        state.selectedRange shouldBe Range.DAYS_365
        state.visibleRanges shouldBe Range.entries
        h.sinceShouldMatch(Range.DAYS_365)
    }

    @Test
    fun `lowering the retention falls back to the longest visible range`() = runTest2 {
        val h = harness(isPro = true, retention = Duration.ofDays(365))
        advanceUntilIdle()
        h.vm.selectRange(Range.DAYS_365)
        advanceUntilIdle()

        h.retention.value = Duration.ofDays(179)
        advanceUntilIdle()

        h.vm.state.first().apply {
            selectedRange shouldBe Range.DAYS_90
            visibleRanges shouldBe listOf(Range.DAYS_7, Range.DAYS_30, Range.DAYS_90)
        }
        h.sinceShouldMatch(Range.DAYS_90)

        h.retention.value = Duration.ofDays(7)
        advanceUntilIdle()

        h.vm.state.first().apply {
            selectedRange shouldBe Range.DAYS_7
            visibleRanges shouldBe listOf(Range.DAYS_7)
        }
        h.sinceShouldMatch(Range.DAYS_7)
    }

    @Test
    fun `losing Pro while one year is selected falls back to 7 days`() = runTest2 {
        val h = harness(isPro = true, retention = Duration.ofDays(365))
        advanceUntilIdle()
        h.vm.selectRange(Range.DAYS_365)
        advanceUntilIdle()

        h.upgradeInfo.value = info(isPro = false)
        advanceUntilIdle()

        h.vm.state.first().apply {
            isPro shouldBe false
            selectedRange shouldBe Range.DAYS_7
        }
        h.sinceShouldMatch(Range.DAYS_7)
    }

    @Test
    fun `retention and entitlement changing together settle on the policy result`() = runTest2 {
        val h = harness(isPro = true, retention = Duration.ofDays(365))
        advanceUntilIdle()
        h.vm.selectRange(Range.DAYS_365)
        advanceUntilIdle()

        h.retention.value = Duration.ofDays(179)
        h.upgradeInfo.value = info(isPro = false)
        advanceUntilIdle()

        SpaceHistoryViewModel.effectiveRange(Range.DAYS_365, Duration.ofDays(179), isPro = false).let { expected ->
            h.vm.state.first().apply {
                isPro shouldBe false
                selectedRange shouldBe expected
                visibleRanges shouldBe SpaceHistoryViewModel.visibleRanges(Duration.ofDays(179))
            }
            h.sinceShouldMatch(expected)
        }

        h.retention.value = Duration.ofDays(365)
        h.upgradeInfo.value = info(isPro = true)
        advanceUntilIdle()

        SpaceHistoryViewModel.effectiveRange(Range.DAYS_365, Duration.ofDays(365), isPro = true).let { expected ->
            expected shouldBe Range.DAYS_365
            h.vm.state.first().apply {
                isPro shouldBe true
                selectedRange shouldBe expected
                visibleRanges shouldBe Range.entries
            }
            h.sinceShouldMatch(expected)
        }
    }

    companion object {
        private const val STORAGE_ID = "storage-a"
    }
}
