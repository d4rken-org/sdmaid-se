package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThumbnailsFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ThumbnailsFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()

        create().targetAreas().forEach {
            neg(it, "folder", Flag.Dir)
            pos(it, ".thumbnails", Flag.Dir)
            pos(it, "folder/.thumbnails", Flag.Dir)
            pos(it, "folder/.thumbnails/subfolder", Flag.Dir)
            pos(it, "folder/.thumbnails/subfolder/file", Flag.File)

            neg(it, "thumbnails", Flag.Dir)
            neg(it, "my.thumbnails", Flag.Dir)
            neg(it, ".thumbnails-db", Flag.Dir)
        }

        confirm(create())
    }
}
