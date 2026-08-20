package eu.darken.sdmse.analyzer.core

import eu.darken.sdmse.analyzer.core.content.ContentDeleteTask
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.device.DeviceStorageScanner
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.core.storage.categories.ContentCategory
import eu.darken.sdmse.analyzer.core.storage.categories.MediaCategory
import eu.darken.sdmse.analyzer.core.storage.categories.SystemCategory
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.MediaStoreTool
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.util.UUID
import javax.inject.Provider

/**
 * Deleting through the Analyzer used to update the content categories only, leaving
 * [DeviceStorage.spaceFree] (and everything derived from it: the storage card, each category's
 * percentage, the system residual) at its last-scan value until the user hit refresh.
 *
 * These tests pin what a delete publishes: the re-read free space, an arithmetic fallback when that
 * read fails, a recomputed system residual, and - for a delete that dies part way through - the
 * removals that already happened.
 */
class AnalyzerDeleteSpaceTest : BaseTest() {

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
    private val otherStorageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    private val mediaStoreTool = mockk<MediaStoreTool>(relaxed = true)
    private val gatewaySwitch = mockk<GatewaySwitch>(relaxed = true)
    private val deviceScanner = mockk<DeviceStorageScanner>(relaxed = true)

    private fun storage(
        id: StorageId = storageId,
        capacity: Long = 100_000L,
        free: Long = 40_000L,
    ) = DeviceStorage(
        id = id,
        label = "storage".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = capacity,
        spaceFree = free,
        setupIncomplete = false,
    )

    private fun buildAnalyzer(
        storages: Set<DeviceStorage>,
        categories: Map<StorageId, Collection<ContentCategory>>,
    ): Analyzer {
        val storageSetup = mockk<SetupModule>(relaxed = true).apply { every { state } returns emptyFlow() }
        val analyzer = Analyzer(
            appScope = gateScope,
            deviceScanner = Provider { deviceScanner },
            storageScanner = Provider { mockk(relaxed = true) },
            gatewaySwitch = gatewaySwitch,
            appInventorySetupModule = mockk(relaxed = true),
            storageSetupModule = storageSetup,
            mediaStoreTool = mediaStoreTool,
            spaceTracker = mockk(relaxed = true),
        )

        val field = Analyzer::class.java.getDeclaredField("coreState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (field.get(analyzer) as MutableStateFlow<Analyzer.CoreState>).value = Analyzer.CoreState(
            storages = storages,
            categories = categories,
        )

        return analyzer
    }

    private fun path(vararg segments: String): APath = LocalPath.build(*segments)

    private fun file(path: APath, size: Long) = ContentItem(
        path = path,
        lookup = null,
        itemSize = size,
        type = FileType.FILE,
        inaccessible = false,
    )

    private fun dir(path: APath, size: Long = 4096L, children: Collection<ContentItem> = emptySet()) = ContentItem(
        path = path,
        lookup = null,
        itemSize = size,
        type = FileType.DIRECTORY,
        children = children,
        inaccessible = false,
    )

    private fun symlink(path: APath, size: Long) = ContentItem(
        path = path,
        lookup = null,
        itemSize = size,
        type = FileType.SYMBOLIC_LINK,
        inaccessible = false,
    )

    private fun installId(pkgName: String) = InstallId(
        pkgId = Pkg.Id(pkgName),
        userHandle = UserHandle2(0),
    )

    private fun pkgStat(id: InstallId, appData: ContentGroup) = AppCategory.PkgStat(
        pkg = mockk<Installed>().apply { every { installId } returns id },
        isShallow = false,
        appCode = null,
        appData = appData,
        appMedia = null,
        extraData = null,
    )

    private fun deleteTask(
        group: ContentGroup,
        targets: Set<APath>,
        targetPkg: InstallId? = null,
    ) = ContentDeleteTask(
        storageId = storageId,
        groupId = group.id,
        targetPkg = targetPkg,
        targets = targets,
    )

    private suspend fun Analyzer.currentStorage(id: StorageId = storageId) =
        data.first().storages.single { it.id == id }

    private suspend inline fun <reified T : ContentCategory> Analyzer.currentCategory(): T =
        data.first().categories[storageId]!!.filterIsInstance<T>().single()

    @Test
    fun `app data deletion publishes the re-read free space`() = runTest2 {
        val target = path("storage", "emulated", "0", "Android", "data", "com.example", "cache")
        val group = ContentGroup(label = "App data".toCaString(), contents = setOf(dir(target, size = 5_000L)))
        val owner = installId("com.example")
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(
                storageId to setOf(AppCategory(storageId, pkgStats = mapOf(owner to pkgStat(owner, group)))),
            ),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 45_000L))

        analyzer.submit(deleteTask(group, setOf(target), targetPkg = owner))

        analyzer.currentStorage().spaceFree shouldBe 45_000L
        analyzer.currentCategory<AppCategory>().spaceUsed shouldBe 0L
    }

    @Test
    fun `media deletion publishes the re-read free space and matching sizes`() = runTest2 {
        val dcim = path("storage", "emulated", "0", "DCIM")
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(dir(dcim, size = 4_096L, children = setOf(file(photo, 1_000L)))),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 45_096L))

        val result = analyzer.submit(deleteTask(group, setOf(dcim))) as ContentDeleteTask.Result

        result.affectedSpace shouldBe 5_096L
        analyzer.currentCategory<MediaCategory>().spaceUsed shouldBe 0L
        analyzer.currentStorage().spaceFree shouldBe 45_096L
        // Storage and category moved by the same amount as the reported result.
        analyzer.currentStorage().spaceUsed shouldBe 60_000L - 5_096L
        coVerify { mediaStoreTool.flush() }
    }

    @Test
    fun `a failed free-space re-read falls back to the group size delta`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val link = path("storage", "emulated", "0", "DCIM", "link")
        val group = ContentGroup(
            label = "Media".toCaString(),
            // The symlink has an itemSize but no size, so the flat freed-space sum (1500) and the
            // group-size delta (1000) differ - the storage card must move by the latter.
            contents = setOf(file(photo, 1_000L), symlink(link, 500L)),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } throws IOException("No storage stats")

        val result = analyzer.submit(deleteTask(group, setOf(photo, link))) as ContentDeleteTask.Result

        result.affectedSpace shouldBe 1_500L
        analyzer.currentStorage().spaceFree shouldBe 41_000L
    }

    @Test
    fun `the fallback can not push free space past the capacity`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 5_000L)))
        val analyzer = buildAnalyzer(
            storages = setOf(storage(capacity = 1_000L, free = 900L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } throws IOException("No storage stats")

        analyzer.submit(deleteTask(group, setOf(photo)))

        analyzer.currentStorage().spaceFree shouldBe 1_000L
    }

    @Test
    fun `other storages are left untouched`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 1_000L)))
        val untouched = storage(id = otherStorageId, capacity = 500L, free = 200L)
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L), untouched),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(
            storage(free = 41_000L),
            // A delete never adopts the scan's numbers for storages it didn't touch.
            untouched.copy(spaceFree = 1L),
        )

        analyzer.submit(deleteTask(group, setOf(photo)))

        analyzer.currentStorage().spaceFree shouldBe 41_000L
        analyzer.currentStorage(otherStorageId) shouldBe untouched
    }

    @Test
    fun `the system residual is recomputed against the new used space`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val mediaGroup = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val systemGroup = ContentGroup(label = "System".toCaString())
        val analyzer = buildAnalyzer(
            storages = setOf(storage(capacity = 1_000L, free = 400L)),
            categories = mapOf(
                storageId to setOf(
                    MediaCategory(storageId, setOf(mediaGroup)),
                    // Scan-time residual: 600 used - 100 media.
                    SystemCategory(storageId, setOf(systemGroup), spaceUsedOverride = 500L),
                ),
            ),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(capacity = 1_000L, free = 450L))

        analyzer.submit(deleteTask(mediaGroup, setOf(photo)))

        analyzer.currentCategory<SystemCategory>().spaceUsedOverride shouldBe 550L
    }

    @Test
    fun `a re-read that also moved the capacity is applied as one pair`() = runTest2 {
        val gb = 1_024L * 1_024L * 1_024L
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val mediaGroup = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, gb)))
        val systemGroup = ContentGroup(label = "System".toCaString())
        val analyzer = buildAnalyzer(
            storages = setOf(storage(capacity = 128 * gb, free = 40 * gb)),
            categories = mapOf(
                storageId to setOf(
                    MediaCategory(storageId, setOf(mediaGroup)),
                    // Scan-time residual: 88 GB used - 1 GB media.
                    SystemCategory(storageId, setOf(systemGroup), spaceUsedOverride = 87 * gb),
                ),
            ),
        )
        // The scanner fell back to a different source (whole disk vs data partition), so the capacity
        // moved too. Taking only spaceFree from it would leave 128 GB - 45 GB = 83 GB used, i.e. MORE
        // used space than before the delete.
        coEvery { deviceScanner.scan() } returns setOf(storage(capacity = 110 * gb, free = 45 * gb))

        analyzer.submit(deleteTask(mediaGroup, setOf(photo)))

        analyzer.currentStorage().spaceCapacity shouldBe 110 * gb
        analyzer.currentStorage().spaceFree shouldBe 45 * gb
        analyzer.currentStorage().spaceUsed shouldBe 65 * gb
        // The residual derives from that same pair, not from the cached capacity.
        analyzer.currentCategory<SystemCategory>().spaceUsedOverride shouldBe 65 * gb
    }

    @Test
    fun `a delete that frees nothing leaves the storage entry unchanged`() = runTest2 {
        val unknown = path("storage", "emulated", "0", "DCIM", "unknown")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(ContentItem.fromInaccessible(unknown)),
        )
        val seeded = storage(free = 40_000L)
        val analyzer = buildAnalyzer(
            storages = setOf(seeded),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } throws IOException("No storage stats")

        analyzer.submit(deleteTask(group, setOf(unknown)))

        analyzer.currentStorage() shouldBe seeded
    }

    @Test
    fun `a failure part way through still publishes what was deleted`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val sub = path("storage", "emulated", "0", "DCIM", "sub")
        val nested = path("storage", "emulated", "0", "DCIM", "sub", "clip.mp4")
        val group = ContentGroup(
            label = "Media".toCaString(),
            // filterDistinctRoots() sorts by segment count, so the shallower photo is deleted first.
            contents = setOf(file(photo, 100L), dir(sub, size = 4_096L, children = setOf(file(nested, 200L)))),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 40_100L))
        coEvery { gatewaySwitch.delete(nested, any()) } throws IOException("Delete failed")

        shouldThrow<IOException> { analyzer.submit(deleteTask(group, setOf(photo, nested))) }

        analyzer.currentCategory<MediaCategory>().spaceUsed shouldBe 4_296L
        analyzer.currentStorage().spaceFree shouldBe 40_100L
    }

    @Test
    fun `a cancellation part way through still publishes what was deleted`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val sub = path("storage", "emulated", "0", "DCIM", "sub")
        val nested = path("storage", "emulated", "0", "DCIM", "sub", "clip.mp4")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(file(photo, 100L), dir(sub, size = 4_096L, children = setOf(file(nested, 200L)))),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 40_100L))
        coEvery { gatewaySwitch.delete(nested, any()) } throws CancellationException("Cancelled")

        shouldThrow<CancellationException> { analyzer.submit(deleteTask(group, setOf(photo, nested))) }

        analyzer.currentCategory<MediaCategory>().spaceUsed shouldBe 4_296L
        analyzer.currentStorage().spaceFree shouldBe 40_100L
    }
}
