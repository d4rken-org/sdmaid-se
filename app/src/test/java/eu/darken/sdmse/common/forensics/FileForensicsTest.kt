package eu.darken.sdmse.common.forensics

import android.content.Context
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class FileForensicsTest : BaseTest() {

    @MockK lateinit var context: Context
    @MockK lateinit var pkgRepo: PkgRepo
    @MockK lateinit var csiProcessor: CSIProcessor
    @MockK lateinit var testAreaInfo: AreaInfo
    @MockK lateinit var gatewaySwitch: GatewaySwitch
    @MockK lateinit var pkgOps: PkgOps
    val processors = mutableSetOf<CSIProcessor>()


    @BeforeEach fun setup() {
        MockKAnnotations.init(this)

        coEvery { csiProcessor.identifyArea(any()) } returns testAreaInfo
        processors.add(csiProcessor)

        every { gatewaySwitch.sharedResource } returns mockk<SharedResource<Any>>().apply {
            coEvery { get() } returns mockk<Resource<Any>>().apply {
                every { close() } returns Unit
            }
            every { close() } returns Unit
        }
        every { pkgOps.sharedResource } returns mockk<SharedResource<Any>>().apply {
            coEvery { get() } returns mockk<Resource<Any>>().apply {
                every { close() } returns Unit
            }
            every { close() } returns Unit
        }
    }

    @AfterEach fun teardown() {

    }

    @Test fun init() = runTest2(autoCancel = true) {
        val forensics = FileForensics(this, context, pkgRepo, processors, gatewaySwitch, pkgOps)
        val testPath = LocalPath.build("/test")
        val areaInfo = forensics.identifyArea(testPath)
        areaInfo shouldBe testAreaInfo
    }
}