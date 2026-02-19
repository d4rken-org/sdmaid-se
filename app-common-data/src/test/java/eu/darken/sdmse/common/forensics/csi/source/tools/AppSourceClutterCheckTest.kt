package eu.darken.sdmse.common.forensics.csi.source.tools

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.clutter.ClutterRepo
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppSourceClutterCheckTest : BaseTest() {

    @MockK lateinit var clutterRepo: ClutterRepo

    private lateinit var check: AppSourceClutterCheck

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        check = AppSourceClutterCheck(clutterRepo)
    }

    @Test
    fun `test no clutter match returns empty owners`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "unknown_file"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        coEvery { clutterRepo.match(any(), any()) } returns emptyList()

        val result = check.process(areaInfo)

        result.owners shouldBe emptySet()
    }

    @Test
    fun `test MIUI preinstall_history mapped to com_miui_packageinstaller`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "preinstall_history"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val match = Marker.Match(
            packageNames = setOf("com.miui.packageinstaller".toPkgId()),
            flags = emptySet()
        )
        coEvery { clutterRepo.match(DataArea.Type.APP_APP, listOf("preinstall_history")) } returns listOf(match)

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.miui.packageinstaller".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test MIUI preinstall_package_path mapped to com_miui_packageinstaller`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "preinstall_package_path"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val match = Marker.Match(
            packageNames = setOf("com.miui.packageinstaller".toPkgId()),
            flags = emptySet()
        )
        coEvery { clutterRepo.match(DataArea.Type.APP_APP, listOf("preinstall_package_path")) } returns listOf(match)

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.miui.packageinstaller".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test multiple package names create multiple owners`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "shared_file"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val match = Marker.Match(
            packageNames = setOf(
                "com.example.app1".toPkgId(),
                "com.example.app2".toPkgId()
            ),
            flags = emptySet()
        )
        coEvery { clutterRepo.match(DataArea.Type.APP_APP, listOf("shared_file")) } returns listOf(match)

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.example.app1".toPkgId(), UserHandle2(0)),
            Owner("com.example.app2".toPkgId(), UserHandle2(0))
        )
    }

    @Test
    fun `test clutter match with custodian flag`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "custodian_file"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val match = Marker.Match(
            packageNames = setOf("com.example.custodian".toPkgId()),
            flags = setOf(Marker.Flag.CUSTODIAN)
        )
        coEvery { clutterRepo.match(DataArea.Type.APP_APP, listOf("custodian_file")) } returns listOf(match)

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.example.custodian".toPkgId(), UserHandle2(0), setOf(Marker.Flag.CUSTODIAN))
        )
    }

    @Test
    fun `test multiple clutter matches create combined owners`() = runTest {
        val areaInfo = AreaInfo(
            file = LocalPath.build("/data/app", "multi_match_file"),
            prefix = LocalPath.build("/data/app"),
            dataArea = DataArea(
                path = LocalPath.build("/data/app"),
                type = DataArea.Type.APP_APP,
                userHandle = UserHandle2(0)
            ),
            isBlackListLocation = false
        )

        val match1 = Marker.Match(
            packageNames = setOf("com.example.app1".toPkgId()),
            flags = emptySet()
        )
        val match2 = Marker.Match(
            packageNames = setOf("com.example.app2".toPkgId()),
            flags = setOf(Marker.Flag.CUSTODIAN)
        )
        coEvery { clutterRepo.match(DataArea.Type.APP_APP, listOf("multi_match_file")) } returns listOf(match1, match2)

        val result = check.process(areaInfo)

        result.owners shouldBe setOf(
            Owner("com.example.app1".toPkgId(), UserHandle2(0)),
            Owner("com.example.app2".toPkgId(), UserHandle2(0), setOf(Marker.Flag.CUSTODIAN))
        )
    }
}