package eu.darken.sdmse.common.forensics.csi.source

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.Owner
import eu.darken.sdmse.common.forensics.csi.BaseCSITest
import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.AfterEach
import java.util.*

class AppSourceLibCSITest : BaseCSITest() {

    private val appSourcesArea = mockk<DataArea>().apply {
        every { flags } returns emptySet()
        every { type } returns DataArea.Type.APP_LIB
        every { path } returns LocalPath.build("/data/app-lib")
    }

    private val bases = setOf(
        appSourcesArea.path,
    ).map { it as LocalPath }

    @Before override fun setup() {
        super.setup()

        every { areaManager.areas } returns flowOf(
            setOf(
                appSourcesArea,
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
            val testFile1 = LocalPath.build(base, UUID.randomUUID().toString())
            processor.identifyArea(testFile1)!!.apply {
                type shouldBe DataArea.Type.APP_LIB
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe testFile1.name
                isBlackListLocation shouldBe true
            }
        }
    }

    @Test override fun `fail to determine area`() = runTest {
        val processor = getProcessor()

        processor.identifyArea(LocalPath.build("/data", UUID.randomUUID().toString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/app", UUID.randomUUID().toString())) shouldBe null
        processor.identifyArea(LocalPath.build("/data/data", UUID.randomUUID().toString())) shouldBe null
    }

    @Test fun testProcess_hit() = runTest {
        val processor = getProcessor()

        val packageName = "eu.thedarken.sdm.test".toPkgId()
        mockPkg(packageName)

        val targets = bases.map {
            setOf(
                LocalPath.build(it, "eu.thedarken.sdm.test-1"),
                LocalPath.build(it, "eu.thedarken.sdm.test-12"),
                LocalPath.build(it, "eu.thedarken.sdm.test-123"),
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
                LocalPath.build(it, "eu.thedarken.sdm.test-1/something.so"),
                LocalPath.build(it, "eu.thedarken.sdm.test-1/abc/def"),
                LocalPath.build(it, "eu.thedarken.sdm.test-12/abc/def"),
                LocalPath.build(it, "eu.thedarken.sdm.test-123/abc/def"),
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

        val prefixFree = UUID.randomUUID().toString()
        mockMarker(packageName, DataArea.Type.APP_LIB, prefixFree)

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
            val suffix = UUID.randomUUID().toString()
            val toHit = LocalPath.build(base, suffix)
            val locationInfo = processor.identifyArea(toHit)!!.apply {
                prefix shouldBe "${base.path}/"
                prefixFreePath shouldBe suffix
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
                LocalPath.build(base, "blabla"),
                LocalPath.build(base, "blabla/something.so"),
            )

            for (target in targets) {
                mockPkg.apply {
                    every { packageInfo } returns mockk<PackageInfo>().apply {
                        applicationInfo = mockk<ApplicationInfo>().apply {
                            nativeLibraryDir = "${base.path}/blabla"
                        }
                    }
                }
                val locationInfo = processor.identifyArea(target)!!

                processor.findOwners(locationInfo).apply {
                    owners shouldBe setOf(Owner(packageName))
                    hasKnownUnknownOwner shouldBe false
                }
            }
        }

    }
}