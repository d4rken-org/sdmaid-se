package eu.darken.sdmse.widget

import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.widget.WidgetRenderState.Data.StorageEntry.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

class WidgetDataProviderTest : BaseTest() {

    private val spaceTracker = mockk<SpaceTracker>()
    private val statsSettings = mockk<StatsSettings>()

    private fun provider() = WidgetDataProvider(spaceTracker, statsSettings)

    private fun snapshot(free: Long, capacity: Long) = SpaceTracker.StorageSnapshot(
        storageId = "s",
        spaceFree = free,
        spaceCapacity = capacity,
    )

    private fun stub(
        primary: SpaceTracker.StorageSnapshot?,
        secondary: List<SpaceTracker.StorageSnapshot> = emptyList(),
        freed: Long = 0L,
    ) {
        coEvery { spaceTracker.readPrimaryStorage() } returns primary
        coEvery { spaceTracker.readSecondaryStorages() } returns secondary
        every { statsSettings.totalSpaceFreed } returns mockDataStoreValue(freed)
    }

    @Test
    fun `maps primary storage and freed into Data`() = runTest2 {
        stub(primary = snapshot(free = 100, capacity = 500), freed = 42L)

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.size shouldBe 1
        state.storages[0].kind shouldBe Kind.INTERNAL
        state.storages[0].usedBytes shouldBe 400
        state.storages[0].totalBytes shouldBe 500
        state.freedBytes shouldBe 42
    }

    @Test
    fun `includes secondary storages after the primary`() = runTest2 {
        stub(
            primary = snapshot(free = 100, capacity = 500),
            secondary = listOf(snapshot(free = 20, capacity = 200)),
        )

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.size shouldBe 2
        state.storages[0].kind shouldBe Kind.INTERNAL
        state.storages[1].kind shouldBe Kind.EXTERNAL
        state.storages[1].usedBytes shouldBe 180
        state.storages[1].totalBytes shouldBe 200
    }

    @Test
    fun `caps the number of storages`() = runTest2 {
        stub(
            primary = snapshot(free = 1, capacity = 100),
            secondary = (1..5).map { snapshot(free = 1, capacity = 100) },
        )

        provider().snapshot().let {
            it.shouldBeInstanceOf<WidgetRenderState.Data>()
            it.storages.size shouldBe 3
        }
    }

    @Test
    fun `no readable storage is Unavailable`() = runTest2 {
        stub(primary = null, secondary = emptyList())

        provider().snapshot() shouldBe WidgetRenderState.Unavailable
    }

    @Test
    fun `zero-capacity volumes are dropped`() = runTest2 {
        stub(primary = snapshot(free = 0, capacity = 0), secondary = listOf(snapshot(free = 0, capacity = 0)))

        provider().snapshot() shouldBe WidgetRenderState.Unavailable
    }

    @Test
    fun `free exceeding capacity is clamped, not negative`() = runTest2 {
        stub(primary = snapshot(free = 800, capacity = 500))

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.single().usedBytes shouldBe 0
        state.storages.single().totalBytes shouldBe 500
    }

    @Test
    fun `negative freed is coerced to zero`() = runTest2 {
        stub(primary = snapshot(free = 100, capacity = 500), freed = -10L)

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.freedBytes shouldBe 0
    }

    @Test
    fun `usedRatio is a clamped fraction`() {
        WidgetRenderState.Data.StorageEntry(Kind.INTERNAL, usedBytes = 250, totalBytes = 1000).usedRatio shouldBe 0.25f
        WidgetRenderState.Data.StorageEntry(Kind.INTERNAL, usedBytes = 0, totalBytes = 0).usedRatio shouldBe 0f
    }
}
