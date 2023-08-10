package eu.darken.sdmse.common.forensics.csi.source

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppSourceAsecCSITest : BaseCSITest() {

    private val appSourcesArea = DataArea(
        type = DataArea.Type.APP_ASEC,
        path = LocalPath.build("/data/app-asec"),
        userHandle = UserHandle2(-1),
    )

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

    private fun getProcessor() = AppSourceAsecCSI(
        areaManager = areaManager,
        pkgRepo = pkgRepo,
        clutterRepo = clutterRepo,
    )

    @Test override fun `test jurisdiction`() = runTest {
        getProcessor().assertJurisdiction(DataArea.Type.APP_ASEC)
    }

    @Test override fun `determine area successfully`() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val testFile1 = base.child(rngString)
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_ASEC
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

        val targets = bases.map { base ->
            setOf(
                base.child("eu.thedarken.sdm.test-1.asec"),
                base.child("eu.thedarken.sdm.test-12.asec"),
                base.child("eu.thedarken.sdm.test-123.asec"),
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
        mockMarker(packageName, DataArea.Type.APP_ASEC, prefixFree)

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
            val suffix = rngString + ".asec"
            val toHit = base.child(suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe base
                prefixFreeSegments shouldBe listOf(suffix)
            }

            processor.findOwners(locationInfo).apply {
                owners shouldBe setOf(
                    Owner(suffix.toPkgId(), locationInfo.userHandle),
                    Owner(suffix.replace(".asec", "").toPkgId(), locationInfo.userHandle),
                )
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}