package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.MediaStoreTool
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.setup.SetupModule
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID
import javax.inject.Provider

/**
 * The safety-critical contract behind #2441: a [ContentDeleteTask] aimed at read-only content (system, or degraded
 * read-only media) must fail before [Analyzer] touches the filesystem. The guard runs before the delete loop, so a
 * thrown [UnsupportedOperationException] proves no deletion happened.
 */
class AnalyzerDeleteGuardTest : BaseTest() {

    // The Analyzer's sharedResource + init collector need a long-lived scope.
    private val gateScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun cancelGateScope() {
        gateScope.coroutineContext[Job]?.cancel()
    }

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )
    private val group = ContentGroup(label = "group".toCaString())
    private val mediaStoreTool = mockk<MediaStoreTool>(relaxed = true)
    private val gatewaySwitch = mockk<GatewaySwitch>(relaxed = true)

    private fun buildAnalyzer(categories: Collection<ContentCategory>): Analyzer {
        val storageSetup = mockk<SetupModule>(relaxed = true).apply { every { state } returns emptyFlow() }
        val analyzer = Analyzer(
            appScope = gateScope,
            deviceScanner = Provider { mockk(relaxed = true) },
            storageScanner = Provider { mockk(relaxed = true) },
            gatewaySwitch = gatewaySwitch,
            appInventorySetupModule = mockk(relaxed = true),
            storageSetupModule = storageSetup,
            mediaStoreTool = mediaStoreTool,
            spaceTracker = mockk(relaxed = true),
        )

        val field = Analyzer::class.java.getDeclaredField("storageCategories").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(analyzer) as MutableStateFlow<Map<StorageId, Collection<ContentCategory>>>).value =
            mapOf(storageId to categories)

        return analyzer
    }

    private fun deleteTask() = ContentDeleteTask(
        storageId = storageId,
        groupId = group.id,
        targets = setOf(LocalPath.build("storage", "emulated", "0", "DCIM") as APath),
    )

    @Test
    fun `read-only media deletion is blocked before any filesystem access`() = runTest {
        val analyzer = buildAnalyzer(setOf(MediaCategory(storageId, setOf(group), isReadOnly = true)))

        shouldThrow<UnsupportedOperationException> { analyzer.submit(deleteTask()) }

        coVerify(exactly = 0) { gatewaySwitch.delete(any(), any()) }
        coVerify(exactly = 0) { mediaStoreTool.notifyDeleted(any()) }
    }

    @Test
    fun `system content deletion is blocked before any filesystem access`() = runTest {
        val analyzer = buildAnalyzer(setOf(SystemCategory(storageId, setOf(group))))

        shouldThrow<UnsupportedOperationException> { analyzer.submit(deleteTask()) }

        coVerify(exactly = 0) { gatewaySwitch.delete(any(), any()) }
        coVerify(exactly = 0) { mediaStoreTool.notifyDeleted(any()) }
    }
}
