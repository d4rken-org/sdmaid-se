package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.mockDataStoreValue

class PackageCacheFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = PackageCacheFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `only with root`() = runTest {
        PackageCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterPackageCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(true)
            }
        ).isEnabled() shouldBe true

        PackageCacheFilter.Factory(
            settings = mockk<SystemCleanerSettings>().apply {
                coEvery { filterPackageCacheEnabled } returns mockDataStoreValue(true)
            },
            filterProvider = mockk(),
            rootManager = mockk<RootManager>().apply {
                every { useRoot } returns flowOf(false)
            }
        ).isEnabled() shouldBe false
    }

    @Test fun testFilter() = runTest {
        mockDefaults()

        neg(Type.DATA_SYSTEM, "package_cache", Flag.Dir)
        pos(Type.DATA_SYSTEM, "package_cache/5a4d3e8ada3bc05a81de25af031e5c73ab98e882", Flag.Dir)
        pos(
            Type.DATA_SYSTEM,
            "package_cache/5a4d3e8ada3bc05a81de25af031e5c73ab98e882/talkback-16--148769904",
            Flag.File
        )
        pos(
            Type.DATA_SYSTEM,
            "package_cache/5a4d3e8ada3bc05a81de25af031e5c73ab98e882/eu.darken.sdmse-zaY3wxgosAp-GqJ_kiiGVw==-0--472968892",
            Flag.File
        )
        pos(
            Type.DATA_SYSTEM,
            "package_cache/5a4d3e8ada3bc05a81de25af031e5c73ab98e882/framework-res__nosdcard__auto_generated_characteristics_rro.apk-16-1678914651",
            Flag.File
        )

        pos(Type.DATA_SYSTEM, "package_cache/386c48c4d1bdfa4fd5a49f8ecb68907861b1a832", Flag.Dir)
        pos(
            Type.DATA_SYSTEM,
            "package_cache/386c48c4d1bdfa4fd5a49f8ecb68907861b1a832/ATMWifiMeta-16-798664151",
            Flag.File
        )

        confirm(create())
    }
}