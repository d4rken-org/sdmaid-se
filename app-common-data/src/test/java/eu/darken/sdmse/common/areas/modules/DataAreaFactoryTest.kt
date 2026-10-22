package eu.darken.sdmse.common.areas.modules

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.ReadException
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.sharedresource.Resource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.IOException

class DataAreaFactoryTest : BaseTest() {

    private val pkgOps: PkgOps = mockk()
    private val gatewaySwitch: GatewaySwitch = mockk()
    private val shellOps: ShellOps = mockk()
    private val leases = mutableListOf<Resource<Any>>()

    private val sdcardArea = DataArea(
        type = DataArea.Type.SDCARD,
        path = LocalPath.build("/storage/emulated/0"),
        userHandle = UserHandle2(0),
    )
    private val publicDataArea = DataArea(
        type = DataArea.Type.PUBLIC_DATA,
        path = LocalPath.build("/storage/emulated/0/Android/data"),
        userHandle = UserHandle2(0),
    )
    private val failingPath = LocalPath.build("/data/misc")

    @BeforeEach
    fun setup() {
        leases.clear()
        every { pkgOps.sharedResource } returns mockSharedResource()
        every { gatewaySwitch.sharedResource } returns mockSharedResource()
        every { shellOps.sharedResource } returns mockSharedResource()
    }

    private fun mockSharedResource(): SharedResource<Any> {
        val lease = mockk<Resource<Any>>().apply {
            every { close() } returns Unit
        }
        leases.add(lease)
        return mockk<SharedResource<Any>>().apply {
            coEvery { get() } returns lease
        }
    }

    private fun create(vararg modules: DataAreaModule) = DataAreaFactory(
        pkgOps = pkgOps,
        gatewaySwitch = gatewaySwitch,
        shellOps = shellOps,
        areaModules = modules.toSet(),
    )

    private fun verifyLeasesClosed() {
        leases.size shouldBe 3
        leases.forEach { verify(exactly = 1) { it.close() } }
    }

    private class FakeModule(
        private val first: suspend () -> Collection<DataArea> = { emptySet() },
        private val second: suspend (Collection<DataArea>) -> Collection<DataArea> = { emptySet() },
    ) : DataAreaModule {
        override suspend fun firstPass(): Collection<DataArea> = first()

        override suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea> = second(firstPass)
    }

    @Test
    fun `module whose first pass throws an IOException contributes no areas`() = runTest {
        val working = FakeModule(first = { setOf(sdcardArea) })
        val failing = FakeModule(
            first = { throw ReadException(path = failingPath, cause = IOException("adb down")) },
        )

        create(working, failing).build() shouldBe setOf(sdcardArea)

        verifyLeasesClosed()
    }

    @Test
    fun `module whose second pass throws an IOException contributes no areas`() = runTest {
        val working = FakeModule(
            first = { setOf(sdcardArea) },
            second = { setOf(publicDataArea) },
        )
        val failing = FakeModule(
            second = { throw ReadException(path = failingPath, cause = IOException("adb down")) },
        )

        create(working, failing).build() shouldBe setOf(sdcardArea, publicDataArea)

        verifyLeasesClosed()
    }

    @Test
    fun `IllegalArgumentException from a pass propagates`() = runTest {
        val working = FakeModule(first = { setOf(sdcardArea) })
        val failing = FakeModule(first = { throw IllegalArgumentException("bad") })

        shouldThrow<IllegalArgumentException> {
            create(working, failing).build()
        }

        verifyLeasesClosed()
    }

    @Test
    fun `cancellation from a pass propagates`() = runTest {
        val working = FakeModule(first = { setOf(sdcardArea) })
        val failing = FakeModule(second = { throw CancellationException("cancelled") })

        shouldThrow<CancellationException> {
            create(working, failing).build()
        }

        verifyLeasesClosed()
    }
}
