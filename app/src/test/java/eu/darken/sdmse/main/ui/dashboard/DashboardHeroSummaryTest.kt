package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

/**
 * The hero headline must equal exactly what the one-tap DELETE action will free — see
 * [DashboardMainActionEngine.buildHeroSummary].
 */
class DashboardHeroSummaryTest : BaseTest() {

    private fun corpse(size: Long, count: Int) = mockk<CorpseFinder.Data> {
        every { corpses } returns setOf(mockk())
        every { totalSize } returns size
        every { totalCount } returns count
    }

    private fun system(size: Long, count: Int) = mockk<SystemCleaner.Data> {
        every { filterContents } returns setOf(mockk())
        every { totalSize } returns size
        every { totalCount } returns count
    }

    private fun app(size: Long, count: Int) = mockk<AppCleaner.Data> {
        every { junks } returns setOf(mockk())
        every { totalSize } returns size
        every { totalCount } returns count
    }

    private fun dedupe(redundant: Long, removableCount: Int) = mockk<Deduplicator.Data> {
        // Non-empty clusters -> hasData == true; size/count are stubbed directly.
        every { clusters } returns setOf(mockk<Duplicate.Cluster>())
        every { redundantSize } returns redundant
        every { redundantCount } returns removableCount
    }

    private fun oneClick(
        corpse: Boolean = true,
        system: Boolean = true,
        app: Boolean = true,
        dedupe: Boolean = false,
    ) = OneClickOptionsState(
        corpseFinderEnabled = corpse,
        systemCleanerEnabled = system,
        appCleanerEnabled = app,
        deduplicatorEnabled = dedupe,
    )

    @Test
    fun `sums the three default tools when enabled, with data, and Pro`() {
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = system(200, 5),
            app = app(300, 7),
            dedupe = null,
            oneClick = oneClick(),
            isPro = true,
        )!!

        result.totalSize shouldBe 600L
        result.itemCount shouldBe 15
        result.tools.map { it.type } shouldBe listOf(
            SDMTool.Type.CORPSEFINDER,
            SDMTool.Type.SYSTEMCLEANER,
            SDMTool.Type.APPCLEANER,
        )
    }

    @Test
    fun `AppCleaner is excluded when not Pro`() {
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = null,
            app = app(300, 7),
            dedupe = null,
            oneClick = oneClick(),
            isPro = false,
        )!!

        result.totalSize shouldBe 100L
        result.itemCount shouldBe 3
        result.tools.map { it.type } shouldBe listOf(SDMTool.Type.CORPSEFINDER)
    }

    @Test
    fun `a tool with its one-click toggle off is excluded`() {
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = system(200, 5),
            app = null,
            dedupe = null,
            oneClick = oneClick(system = false),
            isPro = true,
        )!!

        result.tools.map { it.type } shouldBe listOf(SDMTool.Type.CORPSEFINDER)
    }

    @Test
    fun `Deduplicator's removable-file count is included in the item count`() {
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = null,
            app = null,
            dedupe = dedupe(redundant = 500, removableCount = 8),
            oneClick = oneClick(dedupe = true),
            isPro = true,
        )!!

        // Size includes dedupe's redundant size; item count sums every tool's removable-file
        // count: 3 corpses + 8 redundant files.
        result.totalSize shouldBe 600L
        result.itemCount shouldBe 11
        result.tools.single { it.type == SDMTool.Type.DEDUPLICATOR }.count shouldBe 8
    }

    @Test
    fun `Deduplicator-only summary reports its removable-file count, not zero`() {
        // Regression for the reported bug: with the deduplicator the only tool with data, the hero
        // showed "0 items can be removed" because the dedup count was excluded from itemCount.
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = null,
            system = null,
            app = null,
            dedupe = dedupe(redundant = 500, removableCount = 8),
            oneClick = oneClick(dedupe = true),
            isPro = true,
        )!!

        result.totalSize shouldBe 500L
        result.itemCount shouldBe 8
        result.tools.map { it.type } shouldBe listOf(SDMTool.Type.DEDUPLICATOR)
    }

    @Test
    fun `Deduplicator is excluded when not Pro`() {
        // The DELETE branch of mainAction only deletes dedupe data for Pro users, so a non-Pro
        // hero must not count it towards "will be freed".
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = null,
            app = null,
            dedupe = dedupe(redundant = 500, removableCount = 4),
            oneClick = oneClick(dedupe = true),
            isPro = false,
        )!!

        result.totalSize shouldBe 100L
        result.tools.map { it.type } shouldBe listOf(SDMTool.Type.CORPSEFINDER)
    }

    @Test
    fun `returns null when nothing is one-tap-actionable for this user`() {
        // AppCleaner has data but the user is not Pro, and it is the only tool with data.
        DashboardMainActionEngine.buildHeroSummary(
            corpse = null,
            system = null,
            app = app(300, 7),
            dedupe = null,
            oneClick = oneClick(),
            isPro = false,
        ).shouldBeNull()
    }

    @Test
    fun `timestamp is the latest scan among the included tools`() {
        val older = Instant.parse("2026-06-10T10:00:00Z")
        val newer = Instant.parse("2026-06-10T11:00:00Z")
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = system(200, 5),
            app = null,
            dedupe = null,
            oneClick = oneClick(),
            isPro = true,
            scanTimes = mapOf(
                SDMTool.Type.CORPSEFINDER to older,
                SDMTool.Type.SYSTEMCLEANER to newer,
            ),
        )!!

        result.timestamp shouldBe newer
    }

    @Test
    fun `a newer scan of an excluded tool does not affect the timestamp`() {
        val shown = Instant.parse("2026-06-10T10:00:00Z")
        val excludedButNewer = Instant.parse("2026-06-10T11:00:00Z")
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = null,
            // Not Pro: AppCleaner is excluded from the summary despite having data and a newer scan.
            app = app(300, 7),
            dedupe = null,
            oneClick = oneClick(),
            isPro = false,
            scanTimes = mapOf(
                SDMTool.Type.CORPSEFINDER to shown,
                SDMTool.Type.APPCLEANER to excludedButNewer,
            ),
        )!!

        result.tools.map { it.type } shouldBe listOf(SDMTool.Type.CORPSEFINDER)
        result.timestamp shouldBe shown
    }

    @Test
    fun `timestamp is null without scan times`() {
        val result = DashboardMainActionEngine.buildHeroSummary(
            corpse = corpse(100, 3),
            system = null,
            app = null,
            dedupe = null,
            oneClick = oneClick(),
            isPro = true,
        )!!

        result.timestamp.shouldBeNull()
    }
}
