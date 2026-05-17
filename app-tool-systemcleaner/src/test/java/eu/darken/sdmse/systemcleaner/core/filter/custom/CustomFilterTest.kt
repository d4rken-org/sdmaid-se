package eu.darken.sdmse.systemcleaner.core.filter.custom

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.core.fakeMatch
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class CustomFilterTest : BaseTest() {

    private fun config(
        identifier: String = "test-filter",
        label: String = "Test filter",
        pathCriteria: Set<SegmentCriterium>? = setOf(
            SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.Start()),
        ),
        areas: Set<DataArea.Type>? = null,
        fileTypes: Set<FileType>? = null,
        pathRegexes: Set<Regex>? = null,
    ): CustomFilterConfig = CustomFilterConfig(
        identifier = identifier,
        label = label,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        modifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
        pathCriteria = pathCriteria,
        areas = areas,
        fileTypes = fileTypes,
        pathRegexes = pathRegexes,
    )

    private fun customFilter(
        cfg: CustomFilterConfig,
        sieve: SystemCrawlerSieve,
    ): CustomFilter {
        val sieveFactory = mockk<SystemCrawlerSieve.Factory>().apply {
            every { create(any()) } returns sieve
        }
        val gatewaySwitch = mockk<GatewaySwitch>(relaxed = true)
        return CustomFilter(
            filterConfig = cfg,
            systemCrawlerSieveFactory = sieveFactory,
            gatewaySwitch = gatewaySwitch,
        )
    }

    @Test
    fun `initialize forwards config fields to sieve config`() = runTest2 {
        val cfg = config(
            areas = setOf(DataArea.Type.SDCARD),
            fileTypes = setOf(FileType.FILE, FileType.DIRECTORY),
            pathCriteria = setOf(SegmentCriterium(listOf("Downloads"), SegmentCriterium.Mode.Start())),
        )
        val sieve = mockk<SystemCrawlerSieve>(relaxed = true)
        val sieveFactory = mockk<SystemCrawlerSieve.Factory>().apply {
            every { create(any()) } returns sieve
        }
        val captured = slot<SystemCrawlerSieve.Config>()
        every { sieveFactory.create(capture(captured)) } returns sieve

        val filter = CustomFilter(
            filterConfig = cfg,
            systemCrawlerSieveFactory = sieveFactory,
            gatewaySwitch = mockk(relaxed = true),
        )
        filter.initialize()

        captured.captured.areaTypes shouldBe setOf(DataArea.Type.SDCARD)
        captured.captured.pathCriteria shouldBe cfg.pathCriteria
    }

    @Test
    fun `targetAreas returns all DataArea types when config areas is null`() = runTest2 {
        val cfg = config(areas = null)
        val sieve = mockk<SystemCrawlerSieve>(relaxed = true)
        val filter = customFilter(cfg, sieve)

        filter.targetAreas() shouldBe DataArea.Type.entries.toSet()
    }

    @Test
    fun `targetAreas returns configured areas when non-null`() = runTest2 {
        val cfg = config(areas = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA))
        val sieve = mockk<SystemCrawlerSieve>(relaxed = true)
        val filter = customFilter(cfg, sieve)

        filter.targetAreas() shouldBe setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_MEDIA)
    }

    @Test
    fun `match returns Match Deletion when sieve matches`() = runTest2 {
        val cfg = config()
        val hit = fakeMatch(name = "hit.dat", size = 100L)
        val sieve = mockk<SystemCrawlerSieve>().apply {
            coEvery { match(any<eu.darken.sdmse.common.files.APathLookup<*>>()) } returns SystemCrawlerSieve.Result(item = hit.lookup, matches = true)
        }
        val filter = customFilter(cfg, sieve)
        filter.initialize()

        val result = filter.match(hit.lookup)

        result.shouldBeInstanceOf<SystemCleanerFilter.Match.Deletion>()
        result.lookup shouldBe hit.lookup
    }

    @Test
    fun `match returns null when sieve does not match`() = runTest2 {
        val cfg = config()
        val miss = fakeMatch(name = "miss.dat")
        val sieve = mockk<SystemCrawlerSieve>().apply {
            coEvery { match(any<eu.darken.sdmse.common.files.APathLookup<*>>()) } returns SystemCrawlerSieve.Result(item = miss.lookup, matches = false)
        }
        val filter = customFilter(cfg, sieve)
        filter.initialize()

        filter.match(miss.lookup) shouldBe null
    }

    @Test
    fun `isUnderdefined is true for regex-only config - BUG-FIXME-8 documents current behavior`() = runTest2 {
        // BUG-FIXME-8: CustomFilterConfig.isUnderdefined checks only pathCriteria and
        // nameCriteria. A config with ONLY pathRegexes set is still considered underdefined
        // even though the sieve can match against pathRegexes. This means the editor's
        // canSave check rejects regex-only filters, and the user cannot save them.
        // Flip this test if isUnderdefined is updated to also consider pathRegexes.
        val cfg = config(
            pathCriteria = null,
            pathRegexes = setOf(Regex(".*\\.tmp\$")),
        )

        cfg.isUnderdefined shouldBe true
    }
}
