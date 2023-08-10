package eu.darken.sdmse.common.forensics.csi.source

import android.content.pm.ApplicationInfo
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.removePrefix
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.AfterEach

class AppSourceLibCSITest : BaseCSITest() {

    private val appSourcesArea = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_LIB
        every { path } returns LocalPath.build("/data/app-lib")
        every { userHandle } returns UserHandle2(0)
    }

    private val bases = setOf(
        appSourcesArea.path,
    )

    @Before override fun setup() {
        super.setup()

        every { areaManager.state } returns flowOf(
            DataAreaManager.State(
                areas = setOf(
                    appSourcesArea,
                )
            )
        )
    }

    @AfterEach override fun teardown() {
        super.teardown()
    }

    private fun getProcessor() = AppSourceLibCSI(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.APP_LIB)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_LIB
                prefix shouldBe base
                prefixFreeSegments shouldBe testFile1.removePrefix(base)
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/app", rngString)) shouldBe null
        processor.identifyArea(LocalPath.build("/data/data", rngString)) shouldBe null
    }

    @Test fun testProcess_hit() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-1"),
                it.child("eu.thedarken.sdm.test-12"),
                it.child("eu.thedarken.sdm.test-123"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_hit_child() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                it.child("eu.thedarken.sdm.test-1/something.so"),
                it.child("eu.thedarken.sdm.test-1/abc/def"),
                it.child("eu.thedarken.sdm.test-12/abc/def"),
                it.child("eu.thedarken.sdm.test-123/abc/def"),
            )
        }.flatten()

        for (toHit in targets) {
            val locationInfo = processor.identifyArea(toHit)!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_clutter_hit() = runTest {
        val processor = getProcessor()

        val packageName = "some.pkg".toPkgId()

        val prefixFree = rngString
        mockMarker(packageName, DataArea.Type.APP_LIB, prefixFree)

        for (base in bases) {

            val locationInfo = processor.identifyArea(base.child(prefixFree))!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_nothing() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val suffix = rngString
            val toHit = base.child(suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
                prefixFreeSegments shouldBe listOf(suffix)
            }

            processor.findOwners(locationInfo).apply {
                owners shouldBe emptySet()
                hasKnownUnknownOwner shouldBe false
            }
        }
    }

    @Test fun testProcess_hit_nativeLibraryDir() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val packageName = "some.pkg".toPkgId()
            val mockPkg = mockPkg(packageName)

            val targets = setOf(
                base.child("blabla"),
                base.child("blabla/something.so"),
            )

            for (target in targets) {
                mockPkg.apply {
                    every { applicationInfo } returns mockk<ApplicationInfo>().apply {
                        nativeLibraryDir = "${base.path}/blabla"
                    }
                }
                val areaInfo = processor.identifyArea(target)!!

                processor.findOwners(areaInfo).apply {
                    owners shouldBe setOf(Owner(packageName, areaInfo.userHandle))
                    hasKnownUnknownOwner shouldBe false
                }
            }
        }

    }
}