package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MacFilesFilterTest : SystemCleanerFilterTest() {

    private val volumeRootAreas = setOf(
        DataArea.Type.SDCARD,
        DataArea.Type.PORTABLE,
    )

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = MacFilesFilter(
        sieveFactory = object : SystemCrawlerSieve.Factory {
            override fun create(config: SystemCrawlerSieve.Config): SystemCrawlerSieve =
                SystemCrawlerSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    private suspend fun forEachTargetArea(block: suspend (DataArea.Type) -> Unit) {
        val areas = create().targetAreas()
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .distinctBy { it.type }
            .forEach { block(it.type) }
    }

    @Test fun `matches macOS file artifacts only as files`() = runTest {
        mockDefaults()
        forEachTargetArea { loc ->
            neg(loc, "folder", Flag.Dir)
            if (loc == DataArea.Type.PUBLIC_MEDIA) neg(loc, "Pictures", Flag.Dir)

            pos(loc, "._something", Flag.File)
            pos(loc, "folder/._something", Flag.File)
            pos(loc, "Pictures/._HLE", Flag.File)
            pos(loc, "._rollkuchen#,'Ä", Flag.File)
            pos(loc, "folder/._rollkuchen#,'Ä", Flag.File)
            pos(loc, "._.Trashes", Flag.File)
            pos(loc, "folder/._.Trashes", Flag.File)
            pos(loc, ".DS_Store", Flag.File)
            pos(loc, "folder/.DS_Store", Flag.File)

            neg(loc, ".Trashes", Flag.File)
            neg(loc, "folder/.Trashes", Flag.File)
            neg(loc, ".spotlight", Flag.File)
            neg(loc, "folder/.spotlight", Flag.File)
            neg(loc, ".Spotlight-V100", Flag.File)
            neg(loc, "folder/.Spotlight-V100", Flag.File)
            neg(loc, ".fseventsd", Flag.File)
            neg(loc, "folder/.fseventsd", Flag.File)
            neg(loc, ".TemporaryItems", Flag.File)
            neg(loc, "folder/.TemporaryItems", Flag.File)
        }
        neg(DataArea.Type.PUBLIC_DATA, "._rollkuchen#,'Ä", Flag.File)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg", Flag.Dir)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg/._rollkuchen#,'Ä", Flag.File)
        confirm(create())
    }

    @Test fun `matches macOS directory artifacts only as directories`() = runTest {
        mockDefaults()
        forEachTargetArea { loc ->
            neg(loc, "folder", Flag.Dir)
            if (loc == DataArea.Type.PUBLIC_MEDIA) neg(loc, "Pictures", Flag.Dir)

            if (loc in volumeRootAreas) {
                pos(loc, ".Trashes", Flag.Dir)
                pos(loc, ".spotlight", Flag.Dir)
                pos(loc, ".Spotlight-V100", Flag.Dir)
                pos(loc, ".fseventsd", Flag.Dir)
                pos(loc, ".TemporaryItems", Flag.Dir)
            } else {
                neg(loc, ".Trashes", Flag.Dir)
                neg(loc, ".spotlight", Flag.Dir)
                neg(loc, ".Spotlight-V100", Flag.Dir)
                neg(loc, ".fseventsd", Flag.Dir)
                neg(loc, ".TemporaryItems", Flag.Dir)
            }

            neg(loc, "._something", Flag.Dir)
            neg(loc, "folder/._something", Flag.Dir)
            neg(loc, "Pictures/._HLE", Flag.Dir)
            neg(loc, "._rollkuchen#,'Ä", Flag.Dir)
            neg(loc, "folder/._rollkuchen#,'Ä", Flag.Dir)
            neg(loc, "._.Trashes", Flag.Dir)
            neg(loc, "folder/._.Trashes", Flag.Dir)
            neg(loc, ".DS_Store", Flag.Dir)
            neg(loc, "folder/.DS_Store", Flag.Dir)
            neg(loc, "folder/.Trashes", Flag.Dir)
            neg(loc, "folder/.spotlight", Flag.Dir)
            neg(loc, "folder/.Spotlight-V100", Flag.Dir)
            neg(loc, "folder/.fseventsd", Flag.Dir)
            neg(loc, "folder/.TemporaryItems", Flag.Dir)
        }
        neg(DataArea.Type.PUBLIC_DATA, "._rollkuchen#,'Ä", Flag.Dir)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg", Flag.Dir)
        neg(DataArea.Type.PUBLIC_DATA, "some.pkg/._rollkuchen#,'Ä", Flag.Dir)
        confirm(create())
    }

    @Test fun `matches file and directory artifacts in a single filter pass`() = runTest {
        mockDefaults()
        // Both sieves must fire within one filter instance: files via fileSieve, dirs via directorySieve
        neg(DataArea.Type.SDCARD, "folder", Flag.Dir)

        pos(DataArea.Type.SDCARD, "._something", Flag.File)
        pos(DataArea.Type.SDCARD, "folder/.DS_Store", Flag.File)
        pos(DataArea.Type.SDCARD, ".Trashes", Flag.Dir)
        pos(DataArea.Type.SDCARD, ".fseventsd", Flag.Dir)

        neg(DataArea.Type.SDCARD, "folder/.Trashes", Flag.Dir)
        neg(DataArea.Type.SDCARD, ".Trashes", Flag.File)
        confirm(create())
    }

    @Test fun `volume directory artifacts are restricted to SDCARD and PORTABLE`() = runTest {
        mockDefaults()
        // Pin the SDCARD/PORTABLE-only restriction so it can't silently widen to PUBLIC_MEDIA
        pos(DataArea.Type.SDCARD, ".Trashes", Flag.Dir)
        pos(DataArea.Type.PORTABLE, ".Trashes", Flag.Dir)

        neg(DataArea.Type.PUBLIC_MEDIA, ".Trashes", Flag.Dir)
        neg(DataArea.Type.PUBLIC_MEDIA, ".Spotlight-V100", Flag.Dir)
        neg(DataArea.Type.PUBLIC_MEDIA, ".fseventsd", Flag.Dir)
        neg(DataArea.Type.PUBLIC_MEDIA, ".TemporaryItems", Flag.Dir)
        confirm(create())
    }
}
