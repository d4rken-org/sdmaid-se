package eu.darken.sdmse.setup.root

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.root.service.RootServiceConnection
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.flow.test

class RootSetupModuleTest : BaseTest() {

    private val rootSettings: RootSettings = mockk()
    private val rootManager: RootManager = mockk()
    private val dataAreaManager: DataAreaManager = mockk(relaxed = true)

    private val useRootValue: DataStoreValue<Boolean?> = mockk()
    private lateinit var useRootFlow: MutableStateFlow<Boolean?>
    private val connection: RootServiceClient.Connection = mockk()
    private val ipc: RootServiceConnection = mockk()
    private lateinit var scope: CoroutineScope
    private var probeCount = 0

    @BeforeEach
    fun setup() {
        probeCount = 0
        useRootFlow = MutableStateFlow(true)
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

        every { rootSettings.useRoot } returns useRootValue
        every { useRootValue.flow } returns useRootFlow

        coEvery { rootManager.isInstalled() } returns true
        every { rootManager.binder } returns flowOf(connection)
        every { connection.ipc } returns ipc
        every { ipc.checkBase() } answers { probeCount++; "ok" }
    }

    @AfterEach
    fun teardown() {
        scope.cancel()
    }

    private fun module() = RootSetupModule(scope, rootSettings, rootManager, dataAreaManager)

    @Test fun `first subscription emits Loading then Result`() {
        val mod = module()

        val collector = mod.state.test(tag = "first", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }

        collector.latestValues.first().shouldBeInstanceOf<RootSetupModule.Loading>()
        val result = collector.latestValues.last().shouldBeInstanceOf<RootSetupModule.Result>()
        result.ourService shouldBe true

        runBlocking { collector.cancelAndJoin() }
    }

    @Test fun `re-subscription emits cached Result instead of Loading`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is RootSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<RootSetupModule.Result>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `setting change while unsubscribed does not replay stale cache`() {
        val mod = module()

        val first = mod.state.test(tag = "first", scope = scope)
        first.await { values, _ -> values.any { it is RootSetupModule.Result } }
        runBlocking { first.cancelAndJoin() }
        runBlocking { delay(50) }

        // User turns root off while nothing observes the module.
        useRootFlow.value = false

        val second = mod.state.test(tag = "second", scope = scope)
        second.await { values, _ -> values.isNotEmpty() }

        second.latestValues.first().shouldBeInstanceOf<RootSetupModule.Loading>()

        runBlocking { second.cancelAndJoin() }
    }

    @Test fun `refresh triggers a fresh probe`() {
        val mod = module()

        val collector = mod.state.test(tag = "refresh", scope = scope)
        collector.await { values, _ -> values.any { it is RootSetupModule.Result } }
        val before = probeCount

        runBlocking { mod.refresh() }
        collector.await { _, _ -> probeCount > before }

        probeCount shouldBeGreaterThan before

        runBlocking { collector.cancelAndJoin() }
    }
}
