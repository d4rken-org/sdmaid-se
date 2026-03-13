package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShortcutServiceFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = ShortcutServiceFilter(
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `test shortcut service bitmap cache`() = runTest {
        // Real-world example from Pixel 8:
        // /data/system_ce/0/shortcut_service/bitmaps/com.google.android.gm/1760961952236.png

        // Should NOT match - wrong area type
        neg(testPkg, Type.PRIVATE_DATA, "shortcut_service", "bitmaps", testPkg, "1760961952236.png")
        neg(testPkg, Type.PUBLIC_DATA, "shortcut_service", "bitmaps", testPkg, "1760961952236.png")
        neg(testPkg, Type.SDCARD, "shortcut_service", "bitmaps", testPkg, "1760961952236.png")

        // Should NOT match - incomplete path (need at least 4 segments)
        neg(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service")
        neg(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps")
        neg(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", testPkg)

        // Should NOT match - wrong path structure
        neg(testPkg, Type.DATA_SYSTEM_CE, "wrong_path", "bitmaps", testPkg, "1760961952236.png")
        neg(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "wrong", testPkg, "1760961952236.png")

        // Should NOT match - different package name
        neg(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", "com.other.app", "1760961952236.png")

        // Should match - correct structure with matching package and timestamp-based filenames
        pos(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", testPkg, "1760961952236.png")
        pos(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", testPkg, "1761449886250.png")
        pos(testPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", testPkg, "1755708275821.png")

        confirm(create())
    }

    @Test fun `test real world gmail example`() = runTest {
        // Real-world data from Pixel 8: com.google.android.gm has multiple cached shortcut icons
        val gmailPkg = "com.google.android.gm"

        pos(gmailPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", gmailPkg, "1760961952236.png")
        pos(gmailPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", gmailPkg, "1761449886250.png")
        pos(gmailPkg, Type.DATA_SYSTEM_CE, "shortcut_service", "bitmaps", gmailPkg, "1755708275821.png")

        confirm(create())
    }
}
