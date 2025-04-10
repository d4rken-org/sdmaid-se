package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
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

    @Test fun `test defaults`() = runTest {
        addDefaultNegatives()
        neg(testPkg, PUBLIC_DATA, "$testPkg/.trash")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.trash/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.trashfiles")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.trashfiles/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.trashbin")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.trashbin/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.recycle")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.recycle/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.recyclebin")
        pos(testPkg, PUBLIC_DATA, "$testPkg/.recyclebin/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.trash")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.trash/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.trashfiles")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.trashfiles/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.trashbin")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.trashbin/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.recycle")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.recycle/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.recyclebin")
        pos(testPkg, PUBLIC_DATA, "$testPkg/files/.recyclebin/$rngString")
        neg(testPkg, PUBLIC_DATA, "$testPkg/files/.trash/.nomedia")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.trash/.nomedia")
        neg(testPkg, PUBLIC_DATA, "$testPkg/.trashfiles/.nomedia")
        pos(testPkg, PUBLIC_MEDIA, "$testPkg/.trashfiles/${rngString}")
        confirm(create())
    }

    @Test fun `test oneplus gallery`() = runTest {
        addDefaultNegatives()
        neg("com.oneplus.gallery", PRIVATE_DATA, "com.oneplus.gallery/databases/")
        neg("com.oneplus.gallery", PRIVATE_DATA, "com.oneplus.gallery/databases/someother.db")
        pos("com.oneplus.gallery", PRIVATE_DATA, "com.oneplus.gallery/databases/recyclebin.db")
        neg("com.oneplus.gallery", PUBLIC_DATA, "com.oneplus.gallery/files/recyclebin")
        pos("com.oneplus.gallery", PUBLIC_DATA, "com.oneplus.gallery/files/recyclebin/somefiles")
        confirm(create())
    }

    @Test fun `test meizu gallery`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.media.gallery", PUBLIC_DATA, ".MeizuGalleryTrashBin")
        neg("com.meizu.media.gallery", PUBLIC_DATA, ".MeizuGallery/something")
        pos("com.meizu.media.gallery", PUBLIC_DATA, ".MeizuGalleryTrashBin/something123")
        confirm(create())
    }

    @Test fun `test computer launcher`() = runTest {
        addDefaultNegatives()
        neg("com.vietbm.computerlauncher", SDCARD, "RecycleBin")
        pos("com.vietbm.computerlauncher", SDCARD, "RecycleBin/something123")
        confirm(create())
    }

    @Test fun `test es file explorer`() = runTest {
        val pkgs = arrayOf(
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.cupcake",
            "com.estrongs.android.pop.app.shortcut",
            "com.estrongs.android.pop.pro"
        )
        pkgs.forEach { pkg ->
            neg(pkg, SDCARD, ".estrongs/")
            neg(pkg, SDCARD, ".estrongs/something")
            neg(pkg, SDCARD, ".estrongs/recycle")
            pos(pkg, SDCARD, ".estrongs/recycle/$rngString")
        }
        confirm(create())
    }

    @Test fun `test cx inventor file explorer`() = runTest {
        addDefaultNegatives()
        neg("com.cxinventor.file.explorer", SDCARD, ".\$recycle_bin$")
        pos("com.cxinventor.file.explorer", SDCARD, ".\$recycle_bin$/something123")
        confirm(create())
    }

    @Test fun `test alpha inventor file manager`() = runTest {
        addDefaultNegatives()
        neg("com.alphainventor.filemanager", SDCARD, ".\$recycle_bin$")
        pos("com.alphainventor.filemanager", SDCARD, ".\$recycle_bin$/something123")
        confirm(create())
    }

    @Test fun `test meizu garbage`() = runTest {
        addDefaultNegatives()
        neg("com.meizu.filemanager", PUBLIC_DATA, ".com.meizu.filemanager/.garbage")
        pos("com.meizu.filemanager", PUBLIC_DATA, ".com.meizu.filemanager/.garbage/something")
        neg("com.meizu.filemanager", SDCARD, ".recycle")
        pos("com.meizu.filemanager", SDCARD, ".recycle/something")
        confirm(create())
    }

    @Test fun `test smart file manager`() = runTest {
        addDefaultNegatives()
        neg("com.cvinfo.filemanager", SDCARD, ".SFM_trash")
        pos("com.cvinfo.filemanager", SDCARD, ".SFM_trash/something")
        confirm(create())
    }

    @Test fun `test miui gallery cloud trash bin`() = runTest {
        neg("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.trashBin")
        pos("com.miui.gallery", SDCARD, "MIUI/Gallery/cloud/.trashBin/something")
        confirm(create())
    }

    @Test fun `test visky gallery`() = runTest {
        neg("com.visky.gallery", SDCARD, ".Android/.data/com.visky.gallery.data/.data/.secure/.recyclebin")
        pos("com.visky.gallery", SDCARD, ".Android/.data/com.visky.gallery.data/.data/.secure/.recyclebin/something")
        confirm(create())
    }

    @Test fun `test coloros file manager`() = runTest {
        neg("com.coloros.filemanager", SDCARD, ".FileManagerRecycler")
        pos("com.coloros.filemanager", SDCARD, ".FileManagerRecycler/something")
        confirm(create())
    }

    @Test fun `test solid explorer`() = runTest {
        neg("pl.solidexplorer2", SDCARD, ".\$Trash$")
        pos("pl.solidexplorer2", SDCARD, ".\$Trash$/something")
        confirm(create())
    }

    @Test fun `test files by google`() = runTest {
        neg("com.google.android.apps.nbu.files", SDCARD, ".FilesByGoogleTrash")
        pos("com.google.android.apps.nbu.files", SDCARD, ".FilesByGoogleTrash/something")
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