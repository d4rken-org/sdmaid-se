package eu.darken.sdmse.common.adb.shizuku

import eu.darken.porter.sdk.PermissionState
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterAvailability
import eu.darken.porter.sdk.PorterBackend
import eu.darken.porter.sdk.PorterConnection
import eu.darken.porter.sdk.PorterConnectionState
import eu.darken.porter.sdk.PorterIncompatibility
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/** The SDK types have no public constructors, so they are mocked here. */
class PorterGatewayTest : BaseTest() {

    @Test
    fun `connectionChanges emits for refusals and death while the link stays null`() = runTest {
        val state = MutableStateFlow<PorterConnectionState>(PorterConnectionState.Disconnected)
        val connection = MutableStateFlow<PorterConnection?>(null)
        mockkObject(Porter)
        try {
            every { Porter.state } returns state
            every { Porter.connection } returns connection
            val gateway = DefaultPorterGateway(mockk())
            val changes = mutableListOf<Unit>()
            val links = mutableListOf<AdbLink?>()
            backgroundScope.launch { gateway.connectionChanges.collect { changes += it } }
            backgroundScope.launch { gateway.link.collect { links += it } }
            runCurrent()
            changes shouldBe listOf(Unit)
            links shouldBe listOf(null)

            val managerTooOld = mockk<PorterConnectionState.Incompatible> {
                every { incompatibility } returns mockk {
                    every { serverTooOld } returns true
                    every { clientTooOld } returns false
                }
            }
            val clientTooOld = mockk<PorterConnectionState.Incompatible> {
                every { incompatibility } returns mockk {
                    every { serverTooOld } returns false
                    every { clientTooOld } returns true
                }
            }
            listOf(
                managerTooOld,
                PorterConnectionState.Disconnected,
                managerTooOld,
                clientTooOld,
                PorterConnectionState.Disconnected,
            ).forEachIndexed { index, next ->
                state.value = next
                runCurrent()
                changes.size shouldBe index + 2
                links shouldBe listOf(null)
                gateway.link.first() shouldBe null
            }
        } finally {
            unmockkObject(Porter)
        }
    }

    @Test
    fun `connectionChanges follows compatible recovery replacement and death`() = runTest {
        val state = MutableStateFlow<PorterConnectionState>(mockk<PorterConnectionState.Incompatible>())
        val connection = MutableStateFlow<PorterConnection?>(null)
        mockkObject(Porter)
        try {
            every { Porter.state } returns state
            every { Porter.connection } returns connection
            val gateway = DefaultPorterGateway(mockk())
            val changes = mutableListOf<Unit>()
            val links = mutableListOf<AdbLink?>()
            backgroundScope.launch { gateway.connectionChanges.collect { changes += it } }
            backgroundScope.launch { gateway.link.collect { links += it } }
            runCurrent()
            changes.size shouldBe 1
            links shouldBe listOf(null)

            val connections = List(2) {
                mockk<PorterConnection> {
                    every { backend } returns PorterBackend.PORTER
                    every { permission } returns MutableStateFlow(PermissionState.Granted)
                }
            }
            connections.forEachIndexed { index, next ->
                // Match the SDK's publication order: the usable connection precedes its state.
                connection.value = next
                state.value = mockk<PorterConnectionState.Connected> {
                    every { this@mockk.connection } returns next
                }
                runCurrent()
                changes.size shouldBe index + 2
                links.size shouldBe index + 2
                links.last() shouldBe PorterAdbLink(next)
            }

            connection.value = null
            state.value = PorterConnectionState.Disconnected
            runCurrent()
            changes.size shouldBe 4
            links.size shouldBe 4
            links.last() shouldBe null
        } finally {
            unmockkObject(Porter)
        }
    }

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
