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
import eu.darken.sdmse.analyzer.core.storage.toFlatContent
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.PathMapper
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.setup.SetupModule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.util.UUID
import javax.inject.Provider

/**
 * Files deleted in Swiper are dropped from the Analyzer's cached scan result, so an open folder view
 * updates in place instead of listing files that are gone until the next manual scan.
 */
class AnalyzerSwiperPruneTest : BaseTest() {

    private val uncaught = mutableListOf<Throwable>()

    // Mirrors the app scope: no exception handler of its own, anything reaching this one escaped containment.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> uncaught += e },
    )

    @AfterEach
    fun cancelAppScope() {
        appScope.coroutineContext[Job]?.cancel()
    }

    private val storageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )
    private val otherStorageId = StorageId(
        internalId = null,
        externalId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )

    private val gatewaySwitch = mockk<GatewaySwitch>(relaxed = true)
    private val deviceScanner = mockk<DeviceStorageScanner>(relaxed = true)
    private val pathMapper = mockk<PathMapper>().apply {
        coEvery { toSAFPath(any()) } returns null
        coEvery { toLocalPath(any()) } returns null
    }

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
            appScope = appScope,
            deviceScanner = Provider { deviceScanner },
            storageScanner = Provider { mockk(relaxed = true) },
            gatewaySwitch = gatewaySwitch,
            appInventorySetupModule = mockk(relaxed = true),
            storageSetupModule = storageSetup,
            mediaStoreTool = mockk(relaxed = true),
            spaceTracker = mockk(relaxed = true),
            pathMapper = pathMapper,
        )
        analyzer.coreState().value = Analyzer.CoreState(storages = storages, categories = categories)
        return analyzer
    }

    @Suppress("UNCHECKED_CAST")
    private fun Analyzer.coreState(): MutableStateFlow<Analyzer.CoreState> {
        val field = Analyzer::class.java.getDeclaredField("coreState").apply { isAccessible = true }
        return field.get(this) as MutableStateFlow<Analyzer.CoreState>
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

    private fun installId(pkgName: String) = InstallId(
        pkgId = Pkg.Id(pkgName),
        userHandle = UserHandle2(0),
    )

    private fun pkgStat(
        id: InstallId,
        appCode: ContentGroup? = null,
        appData: ContentGroup? = null,
        appMedia: ContentGroup? = null,
    ) = AppCategory.PkgStat(
        pkg = mockk<Installed>().apply { every { installId } returns id },
        isShallow = false,
        appCode = appCode,
        appData = appData,
        appMedia = appMedia,
        extraData = null,
    )

    private suspend fun Analyzer.currentStorage(id: StorageId = storageId) =
        data.first().storages.single { it.id == id }

    private suspend inline fun <reified T : ContentCategory> Analyzer.currentCategory(id: StorageId = storageId): T =
        data.first().categories[id]!!.filterIsInstance<T>().single()

    private suspend fun Analyzer.currentPaths(group: ContentGroup): List<APath> =
        data.first().groups[group.id]!!.contents.toFlatContent().map { it.path }

    @Test
    fun `pruning a media group keeps its id and shrinks it`() = runTest2 {
        val dcim = path("storage", "emulated", "0", "DCIM")
        val photoA = path("storage", "emulated", "0", "DCIM", "a.jpg")
        val photoB = path("storage", "emulated", "0", "DCIM", "b.jpg")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(dir(dcim, children = setOf(file(photoA, 1_000L), file(photoB, 2_000L)))),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage(free = 40_000L)),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 41_000L))

        analyzer.pruneDeleted(setOf(photoA))

        analyzer.currentPaths(group) shouldContainExactlyInAnyOrder listOf(dcim, photoB)
        analyzer.currentCategory<MediaCategory>().spaceUsed shouldBe 4_096L + 2_000L
        analyzer.currentStorage().spaceFree shouldBe 41_000L
    }

    @Test
    fun `pruning an app group keeps its id and shrinks the app`() = runTest2 {
        val cache = path("storage", "emulated", "0", "Android", "data", "com.example", "cache")
        val junk = path("storage", "emulated", "0", "Android", "data", "com.example", "cache", "junk.bin")
        val apk = path("data", "app", "com.example", "base.apk")
        val dataGroup = ContentGroup(
            label = "App data".toCaString(),
            contents = setOf(dir(cache, children = setOf(file(junk, 3_000L)))),
        )
        val codeGroup = ContentGroup(label = "App code".toCaString(), contents = setOf(file(apk, 5_000L)))
        val owner = installId("com.example")
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(
                storageId to setOf(
                    AppCategory(storageId, pkgStats = mapOf(owner to pkgStat(owner, codeGroup, dataGroup))),
                ),
            ),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 43_000L))

        analyzer.pruneDeleted(setOf(junk))

        analyzer.currentPaths(dataGroup) shouldBe listOf(cache)
        val pkg = analyzer.currentCategory<AppCategory>().pkgStats[owner]!!
        pkg.totalSize shouldBe 4_096L + 5_000L
        pkg.appCode shouldBeSameInstanceAs codeGroup
        analyzer.currentStorage().spaceFree shouldBe 43_000L
    }

    @Test
    fun `a prune across groups and storages re-reads free space once and recomputes each residual`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val clip = path("storage", "1234-5678", "Movies", "clip.mp4")
        val keep = path("storage", "1234-5678", "Movies", "keep.mp4")
        val mediaGroup = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val otherMediaGroup = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(file(clip, 200L), file(keep, 50L)),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(
                storage(capacity = 1_000L, free = 400L),
                storage(id = otherStorageId, capacity = 2_000L, free = 1_000L),
            ),
            categories = mapOf(
                storageId to setOf(
                    MediaCategory(storageId, setOf(mediaGroup)),
                    // Scan-time residual: 600 used - 100 media.
                    SystemCategory(storageId, setOf(ContentGroup(label = null)), spaceUsedOverride = 500L),
                ),
                otherStorageId to setOf(
                    MediaCategory(otherStorageId, setOf(otherMediaGroup)),
                    // Scan-time residual: 1000 used - 250 media.
                    SystemCategory(otherStorageId, setOf(ContentGroup(label = null)), spaceUsedOverride = 750L),
                ),
            ),
        )
        // The filesystem freed less than the cached sizes say, so the residuals must move.
        coEvery { deviceScanner.scan() } returns setOf(
            storage(capacity = 1_000L, free = 450L),
            storage(id = otherStorageId, capacity = 2_000L, free = 1_150L),
        )

        analyzer.pruneDeleted(setOf(photo, clip))

        coVerify(exactly = 1) { deviceScanner.scan() }
        analyzer.currentStorage().spaceFree shouldBe 450L
        analyzer.currentStorage(otherStorageId).spaceFree shouldBe 1_150L
        analyzer.currentCategory<MediaCategory>().spaceUsed shouldBe 0L
        analyzer.currentCategory<MediaCategory>(otherStorageId).spaceUsed shouldBe 50L
        // 550 used - 0 media, 850 used - 50 media.
        analyzer.currentCategory<SystemCategory>().spaceUsedOverride shouldBe 550L
        analyzer.currentCategory<SystemCategory>(otherStorageId).spaceUsedOverride shouldBe 800L
    }

    @Test
    fun `a failed free-space re-read falls back to each storage's group size delta`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val clip = path("storage", "1234-5678", "Movies", "clip.mp4")
        val mediaGroup = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val otherMediaGroup = ContentGroup(label = "Media".toCaString(), contents = setOf(file(clip, 200L)))
        val untouched = storage(id = StorageId(internalId = null, externalId = UUID.randomUUID()), free = 7L)
        val analyzer = buildAnalyzer(
            storages = setOf(
                storage(capacity = 1_000L, free = 400L),
                storage(id = otherStorageId, capacity = 2_000L, free = 1_000L),
                untouched,
            ),
            categories = mapOf(
                storageId to setOf(MediaCategory(storageId, setOf(mediaGroup))),
                otherStorageId to setOf(MediaCategory(otherStorageId, setOf(otherMediaGroup))),
            ),
        )
        coEvery { deviceScanner.scan() } throws IOException("No storage stats")

        analyzer.pruneDeleted(setOf(photo, clip))

        analyzer.currentStorage().spaceFree shouldBe 500L
        analyzer.currentStorage(otherStorageId).spaceFree shouldBe 1_200L
        analyzer.currentStorage(untouched.id) shouldBe untouched
    }

    @Test
    fun `a path without a counterpart still prunes items of its own type`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )

        analyzer.pruneDeleted(setOf(photo))

        coVerify { pathMapper.toSAFPath(photo as LocalPath) }
        analyzer.currentPaths(group).shouldBeEmpty()
    }

    @Test
    fun `the same path cached in two groups is pruned from both`() = runTest2 {
        val photo = path("storage", "emulated", "0", "Pictures", "com.example", "img.jpg")
        val appMedia = ContentGroup(label = "App media".toCaString(), contents = setOf(file(photo, 100L)))
        val media = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val owner = installId("com.example")
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(
                storageId to setOf(
                    AppCategory(storageId, pkgStats = mapOf(owner to pkgStat(owner, appMedia = appMedia))),
                    MediaCategory(storageId, setOf(media)),
                ),
            ),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage())

        analyzer.pruneDeleted(setOf(photo))

        analyzer.currentPaths(appMedia).shouldBeEmpty()
        analyzer.currentPaths(media).shouldBeEmpty()
        coVerify(exactly = 1) { deviceScanner.scan() }
    }

    @Test
    fun `a matched zero-byte file still publishes`() = runTest2 {
        val empty = path("storage", "emulated", "0", "Download", "empty.txt")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(empty, 0L)))
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage())

        analyzer.pruneDeleted(setOf(empty))

        analyzer.currentPaths(group).shouldBeEmpty()
        coVerify(exactly = 1) { deviceScanner.scan() }
    }

    @Test
    fun `unrelated, empty and repeated sets publish nothing`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val unrelated = path("storage", "emulated", "0", "Music", "song.mp3")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage(free = 40_100L))
        val seeded = analyzer.coreState().value

        analyzer.pruneDeleted(setOf(unrelated))
        analyzer.pruneDeleted(emptySet())
        analyzer.onPathsDeleted(emptySet())

        analyzer.coreState().value shouldBeSameInstanceAs seeded
        coVerify(exactly = 0) { deviceScanner.scan() }

        analyzer.pruneDeleted(setOf(photo))
        val pruned = analyzer.coreState().value
        analyzer.pruneDeleted(setOf(photo))

        analyzer.coreState().value shouldBeSameInstanceAs pruned
        coVerify(exactly = 1) { deviceScanner.scan() }
    }

    @Test
    fun `nothing cached publishes nothing`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val analyzer = buildAnalyzer(storages = setOf(storage()), categories = emptyMap())
        val seeded = analyzer.coreState().value

        analyzer.pruneDeleted(setOf(photo))

        analyzer.coreState().value shouldBeSameInstanceAs seeded
        coVerify(exactly = 0) { deviceScanner.scan() }
        coVerify(exactly = 0) { pathMapper.toSAFPath(any()) }
    }

    @Test
    fun `a prune waits for a running task to release the lock`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val swiped = path("storage", "emulated", "0", "DCIM", "swiped.jpg")
        val group = ContentGroup(
            label = "Media".toCaString(),
            contents = setOf(file(photo, 100L), file(swiped, 200L)),
        )
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage())
        val deleteGate = CompletableDeferred<Unit>()
        coEvery { gatewaySwitch.delete(photo, any()) } coAnswers { deleteGate.await() }

        val deleting = launch {
            analyzer.submit(
                ContentDeleteTask(storageId = storageId, groupId = group.id, targetPkg = null, targets = setOf(photo)),
            )
        }
        runCurrent()

        analyzer.onPathsDeleted(setOf(swiped))
        runCurrent()

        analyzer.currentPaths(group) shouldContainExactlyInAnyOrder listOf(photo, swiped)
        coVerify(exactly = 0) { deviceScanner.scan() }

        deleteGate.complete(Unit)
        deleting.join()

        analyzer.data.first { it.groups[group.id]!!.contents.isEmpty() }
        coVerify(exactly = 2) { deviceScanner.scan() }
        uncaught.shouldBeEmpty()
    }

    @Test
    fun `a failing prune is contained and releases the lock`() = runTest2 {
        val photo = path("storage", "emulated", "0", "DCIM", "img.jpg")
        val group = ContentGroup(label = "Media".toCaString(), contents = setOf(file(photo, 100L)))
        val analyzer = buildAnalyzer(
            storages = setOf(storage()),
            categories = mapOf(storageId to setOf(MediaCategory(storageId, setOf(group)))),
        )
        coEvery { deviceScanner.scan() } returns setOf(storage())
        coEvery { pathMapper.toSAFPath(any()) } throws IllegalStateException("mapping blew up")

        analyzer.onPathsDeleted(setOf(photo))

        coVerify(exactly = 1) { pathMapper.toSAFPath(any()) }
        uncaught.shouldBeEmpty()
        analyzer.currentPaths(group) shouldBe listOf(photo)

        coEvery { pathMapper.toSAFPath(any()) } returns null
        analyzer.pruneDeleted(setOf(photo))

        analyzer.currentPaths(group).shouldBeEmpty()
    }
}
