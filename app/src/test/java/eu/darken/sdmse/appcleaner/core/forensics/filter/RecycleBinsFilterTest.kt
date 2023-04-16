package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.*
import eu.darken.sdmse.common.rngString
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecycleBinsFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = RecycleBinsFilter(
        jsonBasedSieveFactory = createJsonSieveFactory()
    )

    @Test fun testDefaults() = runTest {
        addDefaultNegatives()
        addCandidate(neg().prefixFree("com.some.app/.trash/"))
        addCandidate(pos().prefixFree("com.some.app/.trash/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.trashfiles/"))
        addCandidate(pos().prefixFree("com.some.app/.trashfiles/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.trashbin/"))
        addCandidate(pos().prefixFree("com.some.app/.trashbin/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.recycle/"))
        addCandidate(pos().prefixFree("com.some.app/.recycle/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/.recyclebin/"))
        addCandidate(pos().prefixFree("com.some.app/.recyclebin/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.trash/"))
        addCandidate(pos().prefixFree("com.some.app/files/.trash/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.trashfiles/"))
        addCandidate(pos().prefixFree("com.some.app/files/.trashfiles/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.trashbin/"))
        addCandidate(pos().prefixFree("com.some.app/files/.trashbin/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.recycle/"))
        addCandidate(pos().prefixFree("com.some.app/files/.recycle/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.recyclebin/"))
        addCandidate(pos().prefixFree("com.some.app/files/.recyclebin/$rngString"))
        addCandidate(neg().prefixFree("com.some.app/files/.trash/.nomedia"))
        addCandidate(neg().prefixFree("com.some.app/.trash/.nomedia"))
        addCandidate(neg().prefixFree("com.some.app/.trashfiles/.nomedia"))
        confirm(create())
    }

    @Test fun testOnePlusGallery() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.oneplus.gallery").locs(PRIVATE_DATA).prefixFree("com.oneplus.gallery/databases/")
        )
        addCandidate(
            neg().pkgs("com.oneplus.gallery").locs(PRIVATE_DATA)
                .prefixFree("com.oneplus.gallery/databases/someother.db")
        )
        addCandidate(
            pos().pkgs("com.oneplus.gallery").locs(PRIVATE_DATA)
                .prefixFree("com.oneplus.gallery/databases/recyclebin.db")
        )
        addCandidate(
            neg().pkgs("com.oneplus.gallery").locs(PUBLIC_DATA)
                .prefixFree("com.oneplus.gallery/files/recyclebin")
        )
        addCandidate(
            pos().pkgs("com.oneplus.gallery").locs(PUBLIC_DATA)
                .prefixFree("com.oneplus.gallery/files/recyclebin/somefiles")
        )
        confirm(create())
    }

    @Test fun testMeizuGallery() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.media.gallery").locs(PUBLIC_DATA).prefixFree(".MeizuGalleryTrashBin")
        )
        addCandidate(
            neg().pkgs("com.meizu.media.gallery").locs(PUBLIC_DATA).prefixFree(".MeizuGallery/something")
        )
        addCandidate(
            pos().pkgs("com.meizu.media.gallery").locs(PUBLIC_DATA)
                .prefixFree(".MeizuGalleryTrashBin/something123")
        )
        confirm(create())
    }

    @Test fun testComputerLauncher() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.vietbm.computerlauncher").locs(SDCARD).prefixFree("RecycleBin"))
        addCandidate(
            pos().pkgs("com.vietbm.computerlauncher").locs(SDCARD).prefixFree("RecycleBin/something123")
        )
        confirm(create())
    }

    @Test fun testESFileExplorer() = runTest {
        val pkgs = arrayOf(
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.cupcake",
            "com.estrongs.android.pop.app.shortcut",
            "com.estrongs.android.pop.pro"
        )
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/something"))
        addCandidate(neg().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/recycle"))
        addCandidate(pos().pkgs(*pkgs).locs(SDCARD).prefixFree(".estrongs/recycle/$rngString"))
        confirm(create())
    }

    @Test fun testCXInventorFileExplorer() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.cxinventor.file.explorer").locs(SDCARD).prefixFree(".\$recycle_bin$"))
        addCandidate(
            pos().pkgs("com.cxinventor.file.explorer").locs(SDCARD).prefixFree(".\$recycle_bin$/something123")
        )
        confirm(create())
    }

    @Test fun testAlphaInventorFileManager() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.alphainventor.filemanager").locs(SDCARD).prefixFree(".\$recycle_bin$"))
        addCandidate(
            pos().pkgs("com.alphainventor.filemanager").locs(SDCARD)
                .prefixFree(".\$recycle_bin$/something123")
        )
        confirm(create())
    }

    @Test fun testMeizuGarbage() = runTest {
        addDefaultNegatives()
        addCandidate(
            neg().pkgs("com.meizu.filemanager").locs(PUBLIC_DATA)
                .prefixFree(".com.meizu.filemanager/.garbage")
        )
        addCandidate(
            pos().pkgs("com.meizu.filemanager").locs(PUBLIC_DATA)
                .prefixFree(".com.meizu.filemanager/.garbage/something")
        )
        addCandidate(neg().pkgs("com.meizu.filemanager").locs(SDCARD).prefixFree(".recycle"))
        addCandidate(pos().pkgs("com.meizu.filemanager").locs(SDCARD).prefixFree(".recycle/something"))
        confirm(create())
    }

    @Test fun testSmartFileManager() = runTest {
        addDefaultNegatives()
        addCandidate(neg().pkgs("com.cvinfo.filemanager").locs(SDCARD).prefixFree(".SFM_trash"))
        addCandidate(pos().pkgs("com.cvinfo.filemanager").locs(SDCARD).prefixFree(".SFM_trash/something"))
        confirm(create())
    }

    @Test fun testMIUIGalleryCloudTrashBin() = runTest {
        addCandidate(neg().pkgs("com.miui.gallery").locs(SDCARD).prefixFree("MIUI/Gallery/cloud/.trashBin"))
        addCandidate(pos().pkgs("com.miui.gallery").locs(SDCARD).prefixFree("MIUI/Gallery/cloud/.trashBin/something"))
        confirm(create())
    }

    @Test fun testViskyGallery() = runTest {
        addCandidate(
            neg().pkgs("com.visky.gallery").locs(SDCARD)
                .prefixFree(".Android/.data/com.visky.gallery.data/.data/.secure/.recyclebin")
        )
        addCandidate(
            pos().pkgs("com.visky.gallery").locs(SDCARD)
                .prefixFree(".Android/.data/com.visky.gallery.data/.data/.secure/.recyclebin/something")
        )
        confirm(create())
    }

    @Test fun testColorOsFileManager() = runTest {
        addCandidate(neg().pkgs("com.coloros.filemanager").locs(SDCARD).prefixFree(".FileManagerRecycler"))
        addCandidate(pos().pkgs("com.coloros.filemanager").locs(SDCARD).prefixFree(".FileManagerRecycler/something"))
        confirm(create())
    }

    @Test fun testSolidExplorer() = runTest {
        addCandidate(neg().pkgs("pl.solidexplorer2").locs(SDCARD).prefixFree(".\$Trash$"))
        addCandidate(pos().pkgs("pl.solidexplorer2").locs(SDCARD).prefixFree(".\$Trash$/something"))
        confirm(create())
    }

    @Test fun testFilesByGoogle() = runTest {
        addCandidate(neg().pkgs("com.google.android.apps.nbu.files").locs(SDCARD).prefixFree(".FilesByGoogleTrash"))
        addCandidate(
            pos().pkgs("com.google.android.apps.nbu.files").locs(SDCARD).prefixFree(".FilesByGoogleTrash/something")
        )
        confirm(create())
    }
}