package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.addCandidate
import eu.darken.sdmse.appcleaner.core.forensics.locs
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pkgs
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.appcleaner.core.forensics.prefixFree
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
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
        jsonBasedSieveFactory = createJsonSieveFactory(),
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testDefaults() = runTest {
        addDefaultNegatives()
        addCandidate(neg().prefixFree("$testPkg/.trash/"))
        addCandidate(pos().prefixFree("$testPkg/.trash/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/.trashfiles/"))
        addCandidate(pos().prefixFree("$testPkg/.trashfiles/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/.trashbin/"))
        addCandidate(pos().prefixFree("$testPkg/.trashbin/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/.recycle/"))
        addCandidate(pos().prefixFree("$testPkg/.recycle/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/.recyclebin/"))
        addCandidate(pos().prefixFree("$testPkg/.recyclebin/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.trash/"))
        addCandidate(pos().prefixFree("$testPkg/files/.trash/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.trashfiles/"))
        addCandidate(pos().prefixFree("$testPkg/files/.trashfiles/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.trashbin/"))
        addCandidate(pos().prefixFree("$testPkg/files/.trashbin/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.recycle/"))
        addCandidate(pos().prefixFree("$testPkg/files/.recycle/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.recyclebin/"))
        addCandidate(pos().prefixFree("$testPkg/files/.recyclebin/$rngString"))
        addCandidate(neg().prefixFree("$testPkg/files/.trash/.nomedia"))
        addCandidate(neg().prefixFree("$testPkg/.trash/.nomedia"))
        addCandidate(neg().prefixFree("$testPkg/.trashfiles/.nomedia"))

        pos(testPkg, PUBLIC_MEDIA, "$testPkg/.trashfiles/${rngString}")
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
        neg("com.meizu.filemanager", PUBLIC_DATA, ".com.meizu.filemanager/.garbage")
        pos("com.meizu.filemanager", PUBLIC_DATA, ".com.meizu.filemanager/.garbage/something")

        neg("com.meizu.filemanager", SDCARD, ".recycle")
        pos("com.meizu.filemanager", SDCARD, ".recycle/something")
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

    @Test fun `samsung gallery bin`() = runTest {
        neg("badpkg", SDCARD, "Android/.Trash/com.sec.android.gallery3d")
        neg("com.sec.android.gallery3d", SDCARD, "Android/.Trash/com.sec.android.gallery3d")
        pos("com.sec.android.gallery3d", SDCARD, "Android/.Trash/com.sec.android.gallery3d/3317166978699451126.mp4")
        confirm(create())
    }

    @Test fun `samsung myfiles bin`() = runTest {
        neg("badpkg", SDCARD, "Android/.Trash/com.sec.android.app.myfiles")
        neg("com.sec.android.app.myfiles", SDCARD, "Android/.Trash/com.sec.android.app.myfiles")
        neg("com.sec.android.app.myfiles", SDCARD, "Android/.Trash/com.sec.android.app.myfiles/.nomedia")
        pos(
            "com.sec.android.app.myfiles",
            SDCARD,
            "Android/.Trash/com.sec.android.app.myfiles/9189e8f0-209f-4402-9851-730e5183f578T3/1710218750858/storage/emulated/0/DCIM/Camera/.!%#@\$/20240311_140825.jpg"
        )
        confirm(create())
    }
}