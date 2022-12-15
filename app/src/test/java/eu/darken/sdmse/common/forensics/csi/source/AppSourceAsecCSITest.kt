package eu.darken.sdmse.common.forensics.csi.source

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.common.randomString
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppSourceAsecCSITest : BaseCSITest() {

    private val appSourcesArea = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_ASEC
        every { path } returns LocalPath.build("/data/app-asec")
    }

    private val bases = setOf(
        appSourcesArea.path,
    ).map { it as LocalPath }

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
            val testFile1 = LocalPath.build(base, randomString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_ASEC
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", randomString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/app", randomString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/data", randomString())) shouldBe null
    }

    @Test fun testProcess_hit() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                LocalPath.build(it, "eu.thedarken.sdm.test-1.asec"),
                LocalPath.build(it, "eu.thedarken.sdm.test-12.asec"),
                LocalPath.build(it, "eu.thedarken.sdm.test-123.asec"),
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

        val prefixFree = randomString()
        mockMarker(packageName, DataArea.Type.APP_ASEC, prefixFree)

        for (base in bases) {

            val locationInfo = processor.identifyArea(LocalPath.build(base, prefixFree))!!
            processor.findOwners(locationInfo).apply {
                owners.single().pkgId shouldBe packageName
            }
        }
    }

    @Test fun testProcess_nothing() = runTest {
        val processor = getProcessor()

        for (base in bases) {
            val suffix = randomString() + ".asec"
            val toHit = LocalPath.build(base, suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe suffix
            }

            processor.findOwners(locationInfo).apply {
                owners shouldBe setOf(
                    Owner(suffix.toPkgId()),
                    Owner(suffix.replace(".asec", "").toPkgId()),
                )
                hasKnownUnknownOwner shouldBe false
            }
        }
    }
}