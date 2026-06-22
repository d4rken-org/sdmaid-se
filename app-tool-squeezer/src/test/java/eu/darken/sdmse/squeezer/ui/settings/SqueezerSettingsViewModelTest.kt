package eu.darken.sdmse.squeezer.ui.settings

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class SqueezerSettingsViewModelTest : BaseTest() {

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Values(
        val includeJpeg: DataStoreValue<Boolean>,
        val includeWebp: DataStoreValue<Boolean>,
        val includeHeic: DataStoreValue<Boolean>,
        val includeVideo: DataStoreValue<Boolean>,
        val skipPreviouslyCompressed: DataStoreValue<Boolean>,
        val writeExifMarker: DataStoreValue<Boolean>,
        val minSizeBytes: DataStoreValue<Long>,
    )

    private class Harness(
        val vm: SqueezerSettingsViewModel,
        val settings: SqueezerSettings,
        val historyDatabase: CompressionHistoryDatabase,
        val values: Values,
    )

    private fun harness(
        includeJpeg: Boolean = true,
        includeWebp: Boolean = true,
        includeHeic: Boolean = false,
        includeVideo: Boolean = false,
        skipPreviouslyCompressed: Boolean = true,
        writeExifMarker: Boolean = false,
        minSizeBytes: Long = SqueezerSettings.MIN_FILE_SIZE,
        historyCount: Int = 0,
        historyDatabaseSize: Long = 0L,
    ): Harness {
        val values = Values(
            includeJpeg = rwDataStoreValue(includeJpeg),
            includeWebp = rwDataStoreValue(includeWebp),
            includeHeic = rwDataStoreValue(includeHeic),
            includeVideo = rwDataStoreValue(includeVideo),
            skipPreviouslyCompressed = rwDataStoreValue(skipPreviouslyCompressed),
            writeExifMarker = rwDataStoreValue(writeExifMarker),
            minSizeBytes = rwDataStoreValue(minSizeBytes),
        )
        val settings = mockk<SqueezerSettings>().apply {
            every { this@apply.includeJpeg } returns values.includeJpeg
            every { this@apply.includeWebp } returns values.includeWebp
            every { this@apply.includeHeic } returns values.includeHeic
            every { this@apply.includeVideo } returns values.includeVideo
            every { this@apply.skipPreviouslyCompressed } returns values.skipPreviouslyCompressed
            every { this@apply.writeExifMarker } returns values.writeExifMarker
            every { this@apply.minSizeBytes } returns values.minSizeBytes
        }
        val historyDatabase = mockk<CompressionHistoryDatabase>(relaxed = true).apply {
            every { count } returns flowOf(historyCount)
            every { databaseSize } returns flowOf(historyDatabaseSize)
        }
        val vm = SqueezerSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
            historyDatabase = historyDatabase,
        )
        return Harness(vm, settings, historyDatabase, values)
    }

    // ─────────────────────────── state ───────────────────────────

    @Test
    fun `state passes through DataStore values seeded with the production defaults`() = runTest2 {
        // The harness defaults mirror the production defaults declared in SqueezerSettings. This
        // verifies the VM's combine() correctly threads each value into State; it does NOT
        // validate the production defaults themselves (those would need a real DataStore). If
        // SqueezerSettings flips a default, update this harness alongside.
        val h = harness()

        val state = h.vm.state.first()
        state.includeJpeg shouldBe true
        state.includeWebp shouldBe true
        state.includeHeic shouldBe false
        state.includeVideo shouldBe false
        state.skipPreviouslyCompressed shouldBe true
        state.writeExifMarker shouldBe false
        state.minSizeBytes shouldBe SqueezerSettings.MIN_FILE_SIZE
        state.historyCount shouldBe 0
        state.historyDatabaseSize shouldBe 0L
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val h = harness(
            includeJpeg = false,
            includeWebp = false,
            includeHeic = true,
            includeVideo = true,
            skipPreviouslyCompressed = false,
            writeExifMarker = true,
            minSizeBytes = 4096L,
            historyCount = 42,
            historyDatabaseSize = 32 * 1024L,
        )

        val state = h.vm.state.first()
        state.includeJpeg shouldBe false
        state.includeWebp shouldBe false
        state.includeHeic shouldBe true
        state.includeVideo shouldBe true
        state.skipPreviouslyCompressed shouldBe false
        state.writeExifMarker shouldBe true
        state.minSizeBytes shouldBe 4096L
        state.historyCount shouldBe 42
        state.historyDatabaseSize shouldBe 32 * 1024L
    }

    // ─────────────────────────── setters ───────────────────────────

    @Test
    fun `setIncludeJpeg writes through`() = runTest2 {
        val h = harness(includeJpeg = false)

        h.vm.setIncludeJpeg(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeJpeg.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeWebp writes through`() = runTest2 {
        val h = harness(includeWebp = false)

        h.vm.setIncludeWebp(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeWebp.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeHeic writes through`() = runTest2 {
        val h = harness(includeHeic = false)

        h.vm.setIncludeHeic(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeHeic.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeVideo writes through`() = runTest2 {
        val h = harness(includeVideo = false)

        h.vm.setIncludeVideo(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeVideo.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setSkipPreviouslyCompressed writes through`() = runTest2 {
        val h = harness(skipPreviouslyCompressed = true)

        h.vm.setSkipPreviouslyCompressed(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.skipPreviouslyCompressed.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setWriteExifMarker writes through`() = runTest2 {
        val h = harness(writeExifMarker = false)

        h.vm.setWriteExifMarker(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.writeExifMarker.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setMinSizeBytes writes through`() = runTest2 {
        val h = harness(minSizeBytes = 1024L)

        h.vm.setMinSizeBytes(8192L)
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minSizeBytes.update(capture(captured)) }
        captured.captured(1024L) shouldBe 8192L
    }

    // ─────────────────────────── history actions ───────────────────────────

    @Test
    fun `clearHistory delegates to the database`() = runTest2 {
        val h = harness()

        h.vm.clearHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.historyDatabase.clear() }
    }
}
