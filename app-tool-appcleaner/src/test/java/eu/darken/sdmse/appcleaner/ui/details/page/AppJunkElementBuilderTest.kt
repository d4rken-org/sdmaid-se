package eu.darken.sdmse.appcleaner.ui.details.page

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThumbnailsFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.common.files.APath
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppJunkElementBuilderTest : BaseTest() {

    private fun mockMatch(path: String, gain: Long): ExpendablesFilter.Match {
        val mockPath = mockk<APath> {
            every { this@mockk.path } returns path
        }
        return mockk {
            every { this@mockk.path } returns mockPath
            every { expectedGain } returns gain
        }
    }

    private fun mockJunk(
        expendables: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>?,
        inaccessibleCache: InaccessibleCache? = null,
    ): AppJunk = mockk {
        every { this@mockk.expendables } returns expendables
        every { this@mockk.inaccessibleCache } returns inaccessibleCache
    }

    @Test
    fun `empty junk yields just Header`() {
        val rows = buildAppJunkElements(mockJunk(expendables = null), collapsed = emptySet())
        rows shouldBe listOf(AppJunkElement.Header)
    }

    @Test
    fun `junk with only inaccessible yields Header and Inaccessible`() {
        val cache = mockk<InaccessibleCache>(relaxed = true)
        val rows = buildAppJunkElements(
            mockJunk(expendables = null, inaccessibleCache = cache),
            collapsed = emptySet(),
        )
        rows shouldBe listOf(
            AppJunkElement.Header,
            AppJunkElement.Inaccessible(cache),
        )
    }

    @Test
    fun `single not-collapsed category lists files sorted by expectedGain descending`() {
        val small = mockMatch("/small", 10)
        val big = mockMatch("/big", 1000)
        val medium = mockMatch("/medium", 100)
        val rows = buildAppJunkElements(
            mockJunk(expendables = mapOf(ThumbnailsFilter::class to listOf(small, big, medium))),
            collapsed = emptySet(),
        )
        rows.size shouldBe 5
        rows[0] shouldBe AppJunkElement.Header
        (rows[1] as AppJunkElement.CategoryHeader).category shouldBe ThumbnailsFilter::class
        (rows[1] as AppJunkElement.CategoryHeader).isCollapsed shouldBe false
        (rows[1] as AppJunkElement.CategoryHeader).totalSize shouldBe 1110L
        (rows[2] as AppJunkElement.FileRow).match shouldBe big
        (rows[3] as AppJunkElement.FileRow).match shouldBe medium
        (rows[4] as AppJunkElement.FileRow).match shouldBe small
    }

    @Test
    fun `collapsed category drops file rows but keeps header`() {
        val match = mockMatch("/x", 10)
        val rows = buildAppJunkElements(
            mockJunk(expendables = mapOf(ThumbnailsFilter::class to listOf(match))),
            collapsed = setOf(ThumbnailsFilter::class),
        )
        rows.size shouldBe 2
        rows[0] shouldBe AppJunkElement.Header
        (rows[1] as AppJunkElement.CategoryHeader).isCollapsed shouldBe true
    }

    @Test
    fun `mixed collapsed state across two categories`() {
        val a = mockMatch("/a", 10)
        val b = mockMatch("/b", 20)
        val rows = buildAppJunkElements(
            mockJunk(
                expendables = mapOf(
                    ThumbnailsFilter::class to listOf(a),
                    DefaultCachesPublicFilter::class to listOf(b),
                ),
            ),
            collapsed = setOf(ThumbnailsFilter::class),
        )
        // Header, ThumbnailsHeader (collapsed, no files), DefaultCachesPublicHeader, b
        rows.size shouldBe 4
        rows[0] shouldBe AppJunkElement.Header
        (rows[1] as AppJunkElement.CategoryHeader).category shouldBe ThumbnailsFilter::class
        (rows[1] as AppJunkElement.CategoryHeader).isCollapsed shouldBe true
        (rows[2] as AppJunkElement.CategoryHeader).category shouldBe DefaultCachesPublicFilter::class
        (rows[2] as AppJunkElement.CategoryHeader).isCollapsed shouldBe false
        (rows[3] as AppJunkElement.FileRow).match shouldBe b
    }

    @Test
    fun `categories with empty match collections are skipped`() {
        val a = mockMatch("/a", 10)
        val rows = buildAppJunkElements(
            mockJunk(
                expendables = mapOf(
                    ThumbnailsFilter::class to emptyList(),
                    DefaultCachesPublicFilter::class to listOf(a),
                ),
            ),
            collapsed = emptySet(),
        )
        // Header, DefaultCachesPublicHeader, a — Thumbnails skipped because matches.isEmpty()
        rows.size shouldBe 3
        (rows[1] as AppJunkElement.CategoryHeader).category shouldBe DefaultCachesPublicFilter::class
        (rows[2] as AppJunkElement.FileRow).match shouldBe a
    }
}
