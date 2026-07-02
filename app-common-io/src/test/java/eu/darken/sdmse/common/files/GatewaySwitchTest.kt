package eu.darken.sdmse.common.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookupExtended
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.files.saf.SAFPathLookupExtended
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.storage.PathMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import testhelper.EmptyApp
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = EmptyApp::class)
class GatewaySwitchTest {

    private val safGateway = mockk<SAFGateway>(relaxed = true)
    private val localGateway = mockk<LocalGateway>(relaxed = true)
    private val mapper = mockk<PathMapper>()

    private fun CoroutineScope.createSwitch(): GatewaySwitch {
        every { localGateway.sharedResource } returns SharedResource.createKeepAlive("local", this)
        every { safGateway.sharedResource } returns SharedResource.createKeepAlive("saf", this)
        return GatewaySwitch(
            appScope = this,
            dispatcherProvider = TestDispatcherProvider(),
            safGateway = safGateway,
            localGateway = localGateway,
            mapper = mapper,
        )
    }

    @Test fun `lookupExtended delegates to the matching gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        val path = LocalPath.build("/data/data/com.some.app")
        val result = mockk<LocalPathLookupExtended>()
        coEvery { localGateway.lookupExtended(path) } returns result

        switch.lookupExtended(path) shouldBe result
    }

    @Test fun `lookupExtended AUTO falls back to the alternative gateway`() = runTest2(autoCancel = true) {
        val switch = createSwitch()

        val localPath = LocalPath.build("/data/data/com.some.app")
        val safPath = mockk<SAFPath> { every { pathType } returns APath.PathType.SAF }
        val safResult = mockk<SAFPathLookupExtended>()

        coEvery { localGateway.lookupExtended(localPath) } throws ReadException(path = localPath)
        coEvery { mapper.toSAFPath(localPath) } returns safPath
        coEvery { safGateway.lookupExtended(safPath) } returns safResult

        switch.lookupExtended(localPath, GatewaySwitch.Type.AUTO) shouldBe safResult
    }

    @Test fun `lookupExtended AUTO rethrows the original error when the fallback also fails`() =
        runTest2(autoCancel = true) {
            val switch = createSwitch()

            val localPath = LocalPath.build("/data/data/com.some.app")
            val safPath = mockk<SAFPath> { every { pathType } returns APath.PathType.SAF }
            val original = ReadException(message = "original", path = localPath)

            coEvery { localGateway.lookupExtended(localPath) } throws original
            coEvery { mapper.toSAFPath(localPath) } returns safPath
            coEvery { safGateway.lookupExtended(safPath) } throws ReadException(message = "alternative")

            val thrown = shouldThrow<ReadException> {
                switch.lookupExtended(localPath, GatewaySwitch.Type.AUTO)
            }
            thrown shouldBe original
        }
}
