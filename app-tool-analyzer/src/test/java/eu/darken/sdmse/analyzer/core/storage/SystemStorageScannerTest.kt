package eu.darken.sdmse.analyzer.core.storage

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SystemStorageScannerTest : BaseTest() {

    // --- buildExcludedDataDirs ---

    @Test
    fun `hardcoded dirs are always excluded`() {
        val excluded = SystemStorageScanner.buildExcludedDataDirs(emptySet())
        excluded shouldContain "data"
        excluded shouldContain "user"
        excluded shouldContain "user_de"
        excluded shouldContain "media"
    }

    @Test
    fun `DataArea paths under data are dynamically excluded`() {
        val areas = setOf(
            DataArea(
                type = DataArea.Type.APP_APP,
                path = LocalPath.build("data", "app"),
                userHandle = UserHandle2(0),
            ),
            DataArea(
                type = DataArea.Type.APP_APP_PRIVATE,
                path = LocalPath.build("data", "app-private"),
                userHandle = UserHandle2(0),
            ),
        )
        val excluded = SystemStorageScanner.buildExcludedDataDirs(areas)
        excluded shouldContain "app"
        excluded shouldContain "app-private"
    }

    @Test
    fun `DataArea paths NOT under data are ignored`() {
        // On API 30+, PRIVATE_DATA points to /data_mirror, not /data/data
        val areas = setOf(
            DataArea(
                type = DataArea.Type.PRIVATE_DATA,
                path = LocalPath.build("data_mirror", "data_ce", "null", "0"),
                userHandle = UserHandle2(0),
            ),
        )
        val excluded = SystemStorageScanner.buildExcludedDataDirs(areas)
        // Should NOT add "data_mirror" or other non-/data segments
        excluded shouldNotContain "data_mirror"
        excluded shouldNotContain "data_ce"
        // But hardcoded "data" is still present (covers /data/data bind mount)
        excluded shouldContain "data"
    }

    @Test
    fun `ADB-only mode with no DataAreas still has hardcoded exclusions`() {
        // When only ADB is available, DataArea modules may not populate (root-gated)
        val excluded = SystemStorageScanner.buildExcludedDataDirs(emptySet())
        excluded shouldContain "data"
        excluded shouldContain "user"
        excluded shouldContain "user_de"
        excluded shouldContain "media"
        excluded.size shouldBe 4
    }

    @Test
    fun `mixed DataAreas combine hardcoded and dynamic exclusions`() {
        val areas = setOf(
            DataArea(
                type = DataArea.Type.APP_APP,
                path = LocalPath.build("data", "app"),
                userHandle = UserHandle2(0),
            ),
            DataArea(
                type = DataArea.Type.SDCARD,
                path = LocalPath.build("storage", "emulated", "0"),
                userHandle = UserHandle2(0),
            ),
            DataArea(
                type = DataArea.Type.PRIVATE_DATA,
                path = LocalPath.build("data_mirror", "data_ce", "null", "0"),
                userHandle = UserHandle2(0),
            ),
        )
        val excluded = SystemStorageScanner.buildExcludedDataDirs(areas)
        // Hardcoded
        excluded shouldContain "data"
        excluded shouldContain "user"
        excluded shouldContain "user_de"
        excluded shouldContain "media"
        // Dynamic from APP_APP
        excluded shouldContain "app"
        // SDCARD path has no "data" segment → not added
        excluded shouldNotContain "storage"
        excluded shouldNotContain "emulated"
    }

    @Test
    fun `system DataArea types under data are NOT excluded`() {
        val areas = setOf(
            DataArea(
                type = DataArea.Type.DATA,
                path = LocalPath.build("data"),
                userHandle = UserHandle2(0),
            ),
            DataArea(
                type = DataArea.Type.APP_APP,
                path = LocalPath.build("data", "app"),
                userHandle = UserHandle2(0),
            ),
        )
        val excluded = SystemStorageScanner.buildExcludedDataDirs(areas)
        // APP_APP is app-related → "app" excluded
        excluded shouldContain "app"
        // DATA type is NOT app-related → should not cause extra exclusions
        // (its path is just "/data" which has no child segment anyway, but the type filter matters)
        excluded shouldNotContain "system"
        excluded shouldNotContain "misc"
        excluded shouldNotContain "vendor"
        excluded shouldNotContain "system_ce"
        excluded shouldNotContain "system_de"
    }

    // --- addRemainder ---

    @Test
    fun `remainder added when walked size is less than override`() {
        val system = ContentItem.fromInaccessible(LocalPath.build("system"), size = 1_000_000_000L)
        val data = ContentItem.fromInaccessible(LocalPath.build("data"), size = 500_000_000L)

        val items = SystemStorageScanner.addRemainder(
            walkedItems = listOf(system, data),
            spaceUsedOverride = 2_000_000_000L,
        )

        items.size shouldBe 3
        val other = items.singleOrNull { it.path == SystemStorageScanner.OTHER_PATH }
        other.shouldNotBeNull()
        other.inaccessible shouldBe true
        other.itemSize shouldBe 500_000_000L
        items.sumOf { it.size ?: 0L } shouldBe 2_000_000_000L
    }

    @Test
    fun `no remainder when walked size equals override`() {
        val items = SystemStorageScanner.addRemainder(
            walkedItems = listOf(
                ContentItem.fromInaccessible(LocalPath.build("system"), size = 1_000_000_000L),
                ContentItem.fromInaccessible(LocalPath.build("data"), size = 1_000_000_000L),
            ),
            spaceUsedOverride = 2_000_000_000L,
        )

        items.size shouldBe 2
        items.none { it.path == SystemStorageScanner.OTHER_PATH } shouldBe true
    }

    @Test
    fun `no remainder when walked size exceeds override`() {
        val items = SystemStorageScanner.addRemainder(
            walkedItems = listOf(
                ContentItem.fromInaccessible(LocalPath.build("system"), size = 3_000_000_000L),
            ),
            spaceUsedOverride = 2_000_000_000L,
        )

        items.size shouldBe 1
        items.none { it.path == SystemStorageScanner.OTHER_PATH } shouldBe true
    }

    @Test
    fun `content group total matches override with remainder`() {
        val walked = listOf(
            ContentItem.fromInaccessible(LocalPath.build("system"), size = 900_000_000L),
            ContentItem.fromInaccessible(LocalPath.build("vendor"), size = 150_000_000L),
            ContentItem.fromInaccessible(LocalPath.build("product"), size = 4_000_000_000L),
            ContentItem.fromInaccessible(LocalPath.build("data"), size = 600_000_000L),
        )
        val override = 13_000_000_000L

        val items = SystemStorageScanner.addRemainder(walkedItems = walked, spaceUsedOverride = override)

        val group = ContentGroup(label = null, contents = items)
        group.groupSize shouldBe override
    }
}
