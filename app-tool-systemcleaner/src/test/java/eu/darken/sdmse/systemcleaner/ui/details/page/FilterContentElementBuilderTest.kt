package eu.darken.sdmse.systemcleaner.ui.details.page

import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.FilterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.filter.filterIdentifier
import eu.darken.sdmse.systemcleaner.core.filter.stock.EmptyDirectoryFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.ScreenshotsFilter
import eu.darken.sdmse.systemcleaner.core.filter.stock.TrashedFilter
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class FilterContentElementBuilderTest : BaseTest() {

    private fun mockMatch(
        path: String,
        size: Long,
        modifiedAt: Instant = Instant.EPOCH,
    ): SystemCleanerFilter.Match {
        val mockPath = mockk<APath> {
            every { this@mockk.path } returns path
        }
        val mockLookup = mockk<APathLookup<*>> {
            every { lookedUp } returns mockPath
            every { this@mockk.size } returns size
            every { this@mockk.modifiedAt } returns modifiedAt
        }
        return mockk {
            every { lookup } returns mockLookup
            every { this@mockk.path } returns mockPath
            every { expectedGain } returns size
        }
    }

    private fun makeContent(
        identifier: FilterIdentifier,
        items: List<SystemCleanerFilter.Match>,
    ): FilterContent = FilterContent(
        identifier = identifier,
        icon = mockk<ImageVector>(relaxed = true),
        label = mockk<CaString>(relaxed = true),
        description = mockk<CaString>(relaxed = true),
        items = items,
    )

    @Test
    fun `EmptyDirectoryFilter sorts by path ascending`() {
        val a = mockMatch(path = "/a", size = 0)
        val b = mockMatch(path = "/b", size = 0)
        val z = mockMatch(path = "/z", size = 0)

        val rows = buildFilterContentElements(
            makeContent(EmptyDirectoryFilter::class.filterIdentifier, listOf(z, a, b)),
        )

        rows.map { it.match.path.path } shouldBe listOf("/a", "/b", "/z")
        rows.all { !it.showDate && !it.showThumbnailPreview } shouldBe true
    }

    @Test
    fun `TrashedFilter sorts by modifiedAt descending and enables date+preview`() {
        val old = mockMatch(path = "/old", size = 100, modifiedAt = Instant.EPOCH.plusSeconds(100))
        val newer = mockMatch(path = "/new", size = 100, modifiedAt = Instant.EPOCH.plusSeconds(200))

        val rows = buildFilterContentElements(
            makeContent(TrashedFilter::class.filterIdentifier, listOf(old, newer)),
        )

        rows.map { it.match.path.path } shouldBe listOf("/new", "/old")
        rows.all { it.showDate && it.showThumbnailPreview } shouldBe true
    }

    @Test
    fun `ScreenshotsFilter sorts by modifiedAt ascending and enables date+preview`() {
        val old = mockMatch(path = "/old", size = 100, modifiedAt = Instant.EPOCH.plusSeconds(100))
        val newer = mockMatch(path = "/new", size = 100, modifiedAt = Instant.EPOCH.plusSeconds(200))

        val rows = buildFilterContentElements(
            makeContent(ScreenshotsFilter::class.filterIdentifier, listOf(newer, old)),
        )

        rows.map { it.match.path.path } shouldBe listOf("/old", "/new")
        rows.all { it.showDate && it.showThumbnailPreview } shouldBe true
    }

    @Test
    fun `Other filters sort by expectedGain descending and disable date+preview`() {
        val small = mockMatch(path = "/small", size = 10)
        val big = mockMatch(path = "/big", size = 1000)
        val medium = mockMatch(path = "/medium", size = 100)

        val rows = buildFilterContentElements(
            makeContent("eu.darken.something.else.Filter", listOf(small, big, medium)),
        )

        rows.map { it.match.path.path } shouldBe listOf("/big", "/medium", "/small")
        rows.all { !it.showDate && !it.showThumbnailPreview } shouldBe true
    }

    @Test
    fun `Empty content yields empty list`() {
        val rows = buildFilterContentElements(
            makeContent(TrashedFilter::class.filterIdentifier, emptyList()),
        )
        rows shouldBe emptyList()
    }
}
