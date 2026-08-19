package eu.darken.sdmse.stats.core

import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class LowSpaceMonitorTest : BaseTest() {

    private val primaryId = "primary"
    private val secondaryId = "secondary"
    private val capacity = 100_000_000_000L

    /** The automatic threshold for [capacity]: the smaller of 5% and 2 GiB. */
    private val floor = LowStorage.resolveThreshold(capacity, null)

    private val base = LocalDate.parse("2026-06-01")

    private fun snap(storageId: String, day: Long, used: Long) = SpaceSnapshotEntity(
        storageId = storageId,
        recordedAt = base.plusDays(day).atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(12)),
        spaceFree = capacity - used,
        spaceCapacity = capacity,
    )

    /** Seven days of a steady 1 GB/day fill, which yields a [StorageForecast.Filling]. */
    private fun fillingHistory(storageId: String) = (0 until 7).map {
        snap(storageId, it.toLong(), 10_000_000_000L + 1_000_000_000L * it)
    }

    /** Stateful, so the armed latch behaves like the real DataStore across repeated checks. */
    private fun <T> statefulValue(initial: T): DataStoreValue<T> {
        val state = MutableStateFlow(initial)
        return mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns state
            coEvery { update(any()) } answers {
                val mutation = firstArg<(T) -> T?>()
                val old = state.value
                @Suppress("UNCHECKED_CAST")
                val new = mutation(old) as T
                state.value = new
                DataStoreValue.Updated(old = old, new = new)
            }
        }
    }

    private class Harness(
        val monitor: LowSpaceMonitor,
        val notifications: LowSpaceNotifications,
        val armed: DataStoreValue<Boolean>,
        val historyIds: MutableList<String>,
        val upgradeInfo: MutableStateFlow<UpgradeRepo.Info>,
    )

    private fun upgradeInfo(isPro: Boolean, isSettled: Boolean) = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { this@apply.isSettled } returns isSettled
        every { error } returns null
    }

    private fun reading(free: Long = floor - 1) = SpaceTracker.StorageSnapshot(
        storageId = primaryId,
        spaceFree = free,
        spaceCapacity = capacity,
    )

    private fun harness(
        enabled: Boolean = true,
        isPro: Boolean = true,
        isSettled: Boolean = true,
        armed: Boolean = true,
        /** Virtual-time suspension inside the reading, so two checks can genuinely overlap. */
        readDelayMs: Long = 0L,
        primary: SpaceTracker.StorageSnapshot? = SpaceTracker.StorageSnapshot(
            storageId = primaryId,
            spaceFree = floor + 2_500_000_000L,
            spaceCapacity = capacity,
        ),
        history: Map<String, List<SpaceSnapshotEntity>> = mapOf(
            primaryId to fillingHistory(primaryId),
            secondaryId to fillingHistory(secondaryId),
        ),
        postResult: LowSpaceNotifications.PostResult = LowSpaceNotifications.PostResult.POSTED,
    ): Harness {
        val armedValue = statefulValue(armed)
        val settings = mockk<AnalyzerSettings>().apply {
            every { lowSpaceNotificationEnabled } returns statefulValue(enabled)
            every { lowSpaceNotificationArmed } returns armedValue
            every { lowStorageThresholdBytes } returns statefulValue<Long?>(null)
        }
        val spaceTracker = mockk<SpaceTracker>().apply {
            coEvery { readPrimaryStorage() } coAnswers {
                if (readDelayMs > 0L) delay(readDelayMs)
                primary
            }
        }
        val historyIds = mutableListOf<String>()
        val spaceHistoryRepo = mockk<SpaceHistoryRepo>().apply {
            every { getHistory(any(), any()) } answers {
                val id = firstArg<String>()
                historyIds += id
                flowOf(history[id].orEmpty())
            }
        }
        // A StateFlow (which never completes) so a non-Pro isProSettled() takes its documented
        // timeout path instead of throwing on an exhausted flow.
        val infoFlow = MutableStateFlow(upgradeInfo(isPro = isPro, isSettled = isSettled))
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns infoFlow
        }
        val notifications = mockk<LowSpaceNotifications>(relaxed = true).apply {
            every { notifyLowSpace(any(), any()) } returns postResult
        }

        return Harness(
            monitor = LowSpaceMonitor(
                spaceTracker = spaceTracker,
                spaceHistoryRepo = spaceHistoryRepo,
                analyzerSettings = settings,
                upgradeRepo = upgradeRepo,
                notifications = notifications,
            ),
            notifications = notifications,
            armed = armedValue,
            historyIds = historyIds,
            upgradeInfo = infoFlow,
        )
    }

    // ─────────────────────────── the predictive branch ───────────────────────────

    @Test
    fun `an urgent Filling forecast notifies with that forecast`() = runTest2 {
        // Regression guard: BelowFloor is returned the moment free space reaches the threshold, so
        // a trigger on "is low" would make the predictive copy unreachable.
        val h = harness()

        h.monitor.check()

        val forecast = slot<StorageForecast>()
        val free = slot<Long>()
        verify(exactly = 1) { h.notifications.notifyLowSpace(capture(forecast), capture(free)) }
        val filling = forecast.captured as StorageForecast.Filling
        filling.isUrgent shouldBe true
        filling.daysUntilFloor shouldBe 3L
        free.captured shouldBe floor + 2_500_000_000L
    }

    @Test
    fun `history is read for the primary storage only`() = runTest2 {
        // StorageTrendCalculator needs single-volume snapshots; a merged list yields a
        // meaningless rate.
        val h = harness()

        h.monitor.check()

        h.historyIds shouldBe listOf(primaryId)
    }

    // ─────────────────────────── the latch ───────────────────────────

    @Test
    fun `a persistently low volume notifies exactly once`() = runTest2 {
        val h = harness(primary = reading())

        h.monitor.check()
        h.monitor.check()
        h.monitor.check()

        verify(exactly = 1) { h.notifications.notifyLowSpace(StorageForecast.BelowFloor, floor - 1) }
        h.armed.value() shouldBe false
    }

    @Test
    fun `two concurrent checks notify exactly once`() = runTest2 {
        // The six-hourly worker can overlap the observer. Both would read armed=true and post
        // unless the read-decide-post-write transaction is serialized.
        val h = harness(primary = reading(), readDelayMs = 1_000L)

        launch { h.monitor.check() }
        launch { h.monitor.check() }
        advanceUntilIdle()

        verify(exactly = 1) { h.notifications.notifyLowSpace(any(), any()) }
        h.armed.value() shouldBe false
    }

    @Test
    fun `a blocked post leaves the latch armed`() = runTest2 {
        val h = harness(
            primary = reading(),
            postResult = LowSpaceNotifications.PostResult.BLOCKED,
        )

        h.monitor.check()
        h.armed.value() shouldBe true

        // Still armed, so a later run (e.g. after the user grants the permission) tries again.
        h.monitor.check()
        verify(exactly = 2) { h.notifications.notifyLowSpace(any(), any()) }
    }

    @Test
    fun `a failed post leaves the latch armed`() = runTest2 {
        val h = harness(
            primary = reading(),
            postResult = LowSpaceNotifications.PostResult.FAILED,
        )

        h.monitor.check()

        h.armed.value() shouldBe true
    }

    @Test
    fun `a disabled toggle cancels and re-arms`() = runTest2 {
        val h = harness(enabled = false, armed = false, primary = reading())

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
        verify(exactly = 1) { h.notifications.cancel() }
        h.armed.value() shouldBe true
    }

    @Test
    fun `a non-Pro user cancels and re-arms`() = runTest2 {
        val h = harness(isPro = false, armed = false, primary = reading())

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
        verify(exactly = 1) { h.notifications.cancel() }
        h.armed.value() shouldBe true
    }

    @Test
    fun `a calm forecast cancels and re-arms`() = runTest2 {
        val h = harness(
            armed = false,
            // Far above the floor, so the 1 GB/day trend is not urgent.
            primary = reading(free = 60_000_000_000L),
        )

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
        verify(exactly = 1) { h.notifications.cancel() }
        h.armed.value() shouldBe true
    }

    // ─────────────────────────── the observer ───────────────────────────

    @Test
    fun `an unsettled entitlement is not treated as non-Pro, and acts once it settles`() = runTest2 {
        // The GPlay pre-billing seed reports non-Pro even for paying users, so cancelling on it
        // would re-arm and re-notify on every process start.
        val h = harness(primary = reading(), isPro = false, isSettled = false)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        h.monitor.start(scope)
        advanceUntilIdle()

        verify(exactly = 0) { h.notifications.cancel() }
        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }

        h.upgradeInfo.value = upgradeInfo(isPro = true, isSettled = true)
        advanceUntilIdle()

        verify(exactly = 1) { h.notifications.notifyLowSpace(any(), any()) }

        scope.cancel()
    }

    @Test
    fun `a disabled toggle cancels even while the entitlement is unsettled`() = runTest2 {
        // A warning surviving from a previous process has to go now, not once billing settles -
        // the process may exit before that ever happens.
        val h = harness(enabled = false, armed = false, isPro = false, isSettled = false)
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

        h.monitor.start(scope)
        advanceUntilIdle()

        verify(exactly = 1) { h.notifications.cancel() }
        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
        h.armed.value() shouldBe true

        scope.cancel()
    }

    // ─────────────────────────── implausible readings ───────────────────────────

    @Test
    fun `a zero-capacity reading is rejected`() = runTest2 {
        // readPrimaryStorage() can return a non-null 0/0, which would otherwise post "0 B free".
        val h = harness(
            primary = SpaceTracker.StorageSnapshot(storageId = primaryId, spaceFree = 0L, spaceCapacity = 0L),
        )

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
        verify(exactly = 0) { h.notifications.cancel() }
    }

    @Test
    fun `a negative free value is rejected`() = runTest2 {
        val h = harness(primary = reading(free = -1L))

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
    }

    @Test
    fun `free space larger than capacity is rejected`() = runTest2 {
        val h = harness(primary = reading(free = capacity + 1))

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
    }

    @Test
    fun `a missing reading is rejected`() = runTest2 {
        val h = harness(primary = null)

        h.monitor.check()

        verify(exactly = 0) { h.notifications.notifyLowSpace(any(), any()) }
    }

    // ─────────────────────────── resilience ───────────────────────────

    @Test
    fun `check swallows failures so the worker keeps going`() = runTest2 {
        val settings = mockk<AnalyzerSettings>().apply {
            every { lowSpaceNotificationEnabled } returns statefulValue(true)
            every { lowSpaceNotificationArmed } returns statefulValue(true)
            every { lowStorageThresholdBytes } returns statefulValue<Long?>(null)
        }
        val notifications = mockk<LowSpaceNotifications>(relaxed = true)
        val monitor = LowSpaceMonitor(
            spaceTracker = mockk<SpaceTracker>().apply {
                coEvery { readPrimaryStorage() } throws IllegalStateException("boom")
            },
            spaceHistoryRepo = mockk(relaxed = true),
            analyzerSettings = settings,
            upgradeRepo = mockk(relaxed = true),
            notifications = notifications,
        )

        monitor.check()

        verify(exactly = 0) { notifications.notifyLowSpace(any(), any()) }
    }
}
