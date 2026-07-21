package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Unit tests for the dashboard tool card result logic:
 *  - each tool's [*.Data.toScanSuccess] mapping (the single source of truth shared with performScan)
 *  - [resolveScanCardResult]'s state-resolution branches
 */
class DashboardCardResultTest : BaseTest() {

    // --- Per-tool Data.toScanSuccess() mappings ---------------------------------------------------

    @Test
    fun `appcleaner data maps to scan success by item count and size`() {
        val data = AppCleaner.Data(
            junks = listOf(
                mockk<AppJunk> { every { size } returns 400L; every { itemCount } returns 3 },
                mockk<AppJunk> { every { size } returns 100L; every { itemCount } returns 2 },
            ),
        )
        data.toScanSuccess() shouldBe AppCleanerScanTask.Success(itemCount = 5, recoverableSpace = 500L)
    }

    @Test
    fun `corpsefinder data maps to scan success by corpse count and size`() {
        val data = CorpseFinder.Data(
            corpses = listOf(
                mockk<Corpse> { every { size } returns 400L },
                mockk<Corpse> { every { size } returns 100L },
            ),
        )
        data.toScanSuccess() shouldBe CorpseFinderScanTask.Success(itemCount = 2, recoverableSpace = 500L)
    }

    @Test
    fun `systemcleaner data maps to scan success by contained item count and size`() {
        val data = SystemCleaner.Data(
            filterContents = listOf(
                mockk<FilterContent> { every { items } returns listOf(mockk(), mockk(), mockk()); every { size } returns 400L },
                mockk<FilterContent> { every { items } returns listOf(mockk(), mockk()); every { size } returns 100L },
            ),
        )
        data.toScanSuccess() shouldBe SystemCleanerScanTask.Success(itemCount = 5, recoverableSpace = 500L)
    }

    @Test
    fun `deduplicator data maps item count to cluster count not redundant count`() {
        // Trap guard: itemCount must be the cluster count (2), NOT the summed redundantCount (11).
        val data = Deduplicator.Data(
            clusters = setOf(
                mockk<Duplicate.Cluster> { every { redundantSize } returns 400L; every { redundantCount } returns 7 },
                mockk<Duplicate.Cluster> { every { redundantSize } returns 100L; every { redundantCount } returns 4 },
            ),
        )
        data.toScanSuccess() shouldBe DeduplicatorScanTask.Success(itemCount = 2, recoverableSpace = 500L)
    }

    // --- resolveScanCardResult branches -----------------------------------------------------------

    private val frozen = mockk<SDMTool.Task.Result>()
    private val rebuilt = mockk<SDMTool.Task.Result>()

    private fun resolve(
        isInitializing: Boolean = false,
        isWorking: Boolean = false,
        data: String? = "data",
        hasData: Boolean = true,
        lastResult: SDMTool.Task.Result? = frozen,
        lastResultIsScan: Boolean = true,
    ): SDMTool.Task.Result? = resolveScanCardResult(
        isInitializing = isInitializing,
        isWorking = isWorking,
        data = data,
        hasData = hasData,
        lastResult = lastResult,
        lastResultIsScan = lastResultIsScan,
    ) { rebuilt }

    @Test
    fun `while initializing keeps the frozen result without reconstructing`() {
        resolveScanCardResult(
            isInitializing = true,
            isWorking = false,
            data = "data",
            hasData = true,
            lastResult = frozen,
            lastResultIsScan = true,
        ) { error("must not reconstruct while initializing") } shouldBe frozen
    }

    @Test
    fun `while working keeps the frozen result without reconstructing`() {
        resolveScanCardResult(
            isInitializing = false,
            isWorking = true,
            data = "data",
            hasData = true,
            lastResult = frozen,
            lastResultIsScan = true,
        ) { error("must not reconstruct while working") } shouldBe frozen
    }

    @Test
    fun `live data present rebuilds the summary`() {
        resolve(hasData = true) shouldBe rebuilt
    }

    @Test
    fun `empty live data superseding a scan rebuilds to a zero summary`() {
        // exclude-all / scan-found-nothing: data loaded but empty, last result was a scan.
        resolve(hasData = false, lastResultIsScan = true) shouldBe rebuilt
    }

    @Test
    fun `empty live data with a non-scan result keeps the freed outcome`() {
        // post-delete "freed X": empty data, last result is a processing result.
        resolve(hasData = false, lastResultIsScan = false) shouldBe frozen
    }

    @Test
    fun `invalidated null data drops a stale scan result`() {
        resolve(data = null, hasData = false, lastResultIsScan = true) shouldBe null
    }

    @Test
    fun `invalidated null data keeps a non-scan result`() {
        resolve(data = null, hasData = false, lastResultIsScan = false) shouldBe frozen
    }
}
