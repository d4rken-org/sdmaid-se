package eu.darken.sdmse.common.adb.shizuku

import eu.darken.porter.sdk.PermissionState
import eu.darken.porter.sdk.PorterAvailability
import eu.darken.porter.sdk.PorterBackend
import eu.darken.porter.sdk.PorterConnection
import eu.darken.porter.sdk.PorterIncompatibility
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The SDK types have no public constructors, so they are mocked here. */
class PorterGatewayTest {

    @Test
    fun `NotInstalled maps to NotInstalled`() {
        PorterAvailability.NotInstalled.toAdbAvailability() shouldBe AdbAvailability.NotInstalled
    }

    @Test
    fun `an installed but unconnected manager is Installed and not connected`() {
        val availability = mockk<PorterAvailability.InstalledNotConnected> {
            every { backend } returns PorterBackend.PORTER
            every { packageName } returns "eu.darken.porter"
        }

        availability.toAdbAvailability() shouldBe AdbAvailability.Installed(
            backend = AdbBackend.PORTER,
            packageName = "eu.darken.porter",
            connected = false,
        )
    }

    @Test
    fun `an unrecognized fork is still treated as the installed manager`() {
        val availability = mockk<PorterAvailability.InstalledUnrecognized> {
            every { backend } returns PorterBackend.SHIZUKU
            every { packageName } returns "com.example.shizuku.fork"
        }

        availability.toAdbAvailability() shouldBe AdbAvailability.Installed(
            backend = AdbBackend.SHIZUKU,
            packageName = "com.example.shizuku.fork",
            connected = false,
        )
    }

    @Test
    fun `Connected is Installed and connected, the package may be gone`() {
        val availability = mockk<PorterAvailability.Connected> {
            every { backend } returns PorterBackend.SHIZUKU
            every { packageName } returns null
        }

        availability.toAdbAvailability() shouldBe AdbAvailability.Installed(
            backend = AdbBackend.SHIZUKU,
            packageName = null,
            connected = true,
        )
    }

    @Test
    fun `Incompatible carries backend and which side is too old`() {
        val incompatibility = mockk<PorterIncompatibility> {
            every { backend } returns PorterBackend.PORTER
            every { serverTooOld } returns true
            every { clientTooOld } returns false
        }
        val availability = mockk<PorterAvailability.Incompatible> {
            every { this@mockk.incompatibility } returns incompatibility
            every { packageName } returns "eu.darken.porter"
        }

        availability.toAdbAvailability() shouldBe AdbAvailability.Incompatible(
            backend = AdbBackend.PORTER,
            packageName = "eu.darken.porter",
            serverTooOld = true,
            clientTooOld = false,
        )
    }

    @Test
    fun `the link maps the connection's permission state to granted`() = runTest {
        val state = MutableStateFlow<PermissionState>(PermissionState.Denied(permanentlyDenied = false))
        val connection = mockk<PorterConnection> {
            every { backend } returns PorterBackend.PORTER
            every { uid } returns 2000
            every { permission } returns state
        }
        val link = PorterAdbLink(connection)

        link.backend shouldBe AdbBackend.PORTER
        link.uid shouldBe 2000
        link.permission.first() shouldBe false
        state.value = PermissionState.Granted
        link.permission.first() shouldBe true
    }

    @Test
    fun `the link maps every permission state for check and request`() = runTest {
        val expected = mapOf(
            PermissionState.Granted to AdbPermission.GRANTED,
            PermissionState.Denied(permanentlyDenied = false) to AdbPermission.DENIED,
            PermissionState.Denied(permanentlyDenied = true) to AdbPermission.DENIED_PERMANENTLY,
        )
        expected.forEach { (state, mapped) ->
            val connection = mockk<PorterConnection> {
                every { backend } returns PorterBackend.PORTER
                every { permission } returns MutableStateFlow(state)
                coEvery { checkPermission() } returns state
                coEvery { requestPermission() } returns state
            }
            val link = PorterAdbLink(connection)

            link.checkPermission() shouldBe mapped
            link.requestPermission() shouldBe mapped
        }
    }

    @Test
    fun `links are equal only for the same connection`() {
        val connection = mockk<PorterConnection> {
            every { backend } returns PorterBackend.SHIZUKU
            every { permission } returns MutableStateFlow(PermissionState.Granted)
        }
        val other = mockk<PorterConnection> {
            every { backend } returns PorterBackend.SHIZUKU
            every { permission } returns MutableStateFlow(PermissionState.Granted)
        }

        (PorterAdbLink(connection) == PorterAdbLink(connection)) shouldBe true
        (PorterAdbLink(connection) == PorterAdbLink(other)) shouldBe false
    }
}
