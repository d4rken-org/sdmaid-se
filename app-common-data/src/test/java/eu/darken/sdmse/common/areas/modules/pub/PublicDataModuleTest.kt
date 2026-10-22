package eu.darken.sdmse.common.areas.modules.pub

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException

class PublicDataModuleTest : BaseTest() {

    private val gatewaySwitch: GatewaySwitch = mockk()

    private val failingParent = DataArea(
        type = DataArea.Type.SDCARD,
        path = LocalPath.build("/storage/emulated/0"),
        userHandle = UserHandle2(0),
        flags = setOf(DataArea.Flag.PRIMARY),
    )
    private val readableParent = DataArea(
        type = DataArea.Type.SDCARD,
        path = LocalPath.build("/storage/1234-5678"),
        userHandle = UserHandle2(10),
        flags = emptySet(),
    )
    private val failingPath = LocalPath.build("/storage/emulated/0/Android/data")
    private val readablePath = LocalPath.build("/storage/1234-5678/Android/data")

    @BeforeEach
    fun setup() {
        coEvery { gatewaySwitch.canRead(readablePath) } returns true
        coEvery { gatewaySwitch.lookup(readablePath) } returns mockk(relaxed = true)
        coEvery { gatewaySwitch.listFiles(readablePath) } returns emptyFlow()
    }

    private fun create() = PublicDataModule(
        gatewaySwitch = gatewaySwitch,
        pathMapper = mockk(relaxed = true),
        adbManager = mockk(relaxed = true),
        rootManager = mockk(relaxed = true),
    )

    @Test
    fun `area whose canRead throws an IOException is skipped`() = runTest {
        coEvery { gatewaySwitch.canRead(failingPath) } throws ReadException(
            path = failingPath,
            cause = IOException("adb down"),
        )

        val areas = create().secondPass(listOf(failingParent, readableParent))

        areas shouldHaveSize 1
        areas.single().apply {
            type shouldBe DataArea.Type.PUBLIC_DATA
            path shouldBe readablePath
            flags shouldBe readableParent.flags
            userHandle shouldBe readableParent.userHandle
        }
        coVerify(exactly = 0) { gatewaySwitch.lookup(failingPath) }
        coVerify(exactly = 0) { gatewaySwitch.listFiles(failingPath) }
    }

    @Test
    fun `cancellation from canRead propagates`() = runTest {
        coEvery { gatewaySwitch.canRead(failingPath) } throws CancellationException("cancelled")

        shouldThrow<CancellationException> {
            create().secondPass(listOf(failingParent, readableParent))
        }
    }
}
