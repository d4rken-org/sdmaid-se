package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.runTest2
import java.util.UUID
import javax.inject.Provider

/**
 * SAF half of [AnalyzerSwiperPruneTest]. Separate class because `SAFPath` parses its tree URI with
 * `android.net.Uri`, which needs Robolectric (and therefore JUnit 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AnalyzerSwiperPruneSafTest : BaseTest() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun cancelAppScope() {
        appScope.coroutineContext[Job]?.cancel()
    }

    private val treeUri = "content://com.android.externalstorage.documents/tree/primary%3A"

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )

    private val storage = DeviceStorage(
        id = storageId,
        label = "storage".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = 100_000L,
        spaceFree = 40_000L,
        setupIncomplete = false,
    )

    private val deviceScanner = mockk<DeviceStorageScanner>(relaxed = true).apply {
        coEvery { scan() } returns setOf(storage)
    }
    private val pathMapper = mockk<PathMapper>().apply {
        coEvery { toSAFPath(any()) } returns null
        coEvery { toLocalPath(any()) } returns null
    }

    private fun buildAnalyzer(group: ContentGroup): Analyzer {
        val analyzer = Analyzer(
            appScope = appScope,
            deviceScanner = Provider { deviceScanner },
            storageScanner = Provider { mockk(relaxed = true) },
            gatewaySwitch = mockk(relaxed = true),
            appInventorySetupModule = mockk(relaxed = true),
            storageSetupModule = mockk<SetupModule>(relaxed = true).apply { every { state } returns emptyFlow() },
            mediaStoreTool = mockk(relaxed = true),
            spaceTracker = mockk(relaxed = true),
            pathMapper = pathMapper,
        )
        val field = Analyzer::class.java.getDeclaredField("coreState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(analyzer) as MutableStateFlow<Analyzer.CoreState>).value = Analyzer.CoreState(
            storages = setOf(storage),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        return analyzer
    }

    private fun file(path: APath, size: Long) = ContentItem(
        path = path,
        lookup = null,
        itemSize = size,
        type = FileType.FILE,
        inaccessible = false,
    )

    private suspend fun Analyzer.currentPaths(group: ContentGroup): List<APath> =
        data.first().groups[group.id]!!.contents.toFlatContent().map { it.path }

    @Test
    fun `a deleted SAF path prunes the cached local path`() = runTest2 {
        val local = LocalPath.build("storage", "emulated", "0", "DCIM", "img.jpg")
        val saf = SAFPath.build(treeUri, "DCIM", "img.jpg")
        coEvery { pathMapper.toLocalPath(saf) } returns local
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(local, 100L)))
        val analyzer = buildAnalyzer(group)

        analyzer.pruneDeleted(setOf(saf))

        analyzer.currentPaths(group).shouldBeEmpty()
    }

    @Test
    fun `a deleted local path prunes the cached SAF path`() = runTest2 {
        val local = LocalPath.build("storage", "emulated", "0", "DCIM", "img.jpg")
        val dcim = SAFPath.build(treeUri, "DCIM")
        val saf = SAFPath.build(treeUri, "DCIM", "img.jpg")
        coEvery { pathMapper.toSAFPath(local) } returns saf
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(
                ContentItem(
                    path = dcim,
                    lookup = null,
                    itemSize = 4_096L,
                    type = FileType.DIRECTORY,
                    children = setOf(file(saf, 100L)),
                    inaccessible = false,
                ),
            ),
        )
        val analyzer = buildAnalyzer(group)

        analyzer.pruneDeleted(setOf(local))

        analyzer.currentPaths(group) shouldBe listOf(dcim)
    }

    @Test
    fun `an unmappable SAF path prunes only cached SAF paths`() = runTest2 {
        val local = LocalPath.build("storage", "emulated", "0", "DCIM", "img.jpg")
        val saf = SAFPath.build(treeUri, "DCIM", "img.jpg")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(file(local, 100L), file(saf, 100L)),
        )
        val analyzer = buildAnalyzer(group)

        analyzer.pruneDeleted(setOf(saf))

        analyzer.currentPaths(group) shouldBe listOf(local)
    }
}
