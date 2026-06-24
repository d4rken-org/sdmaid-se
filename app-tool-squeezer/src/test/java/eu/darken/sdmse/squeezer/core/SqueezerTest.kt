package eu.darken.sdmse.squeezer.core

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.squeezer.core.processor.ImageProcessor
import eu.darken.sdmse.squeezer.core.processor.VideoProcessor
import eu.darken.sdmse.squeezer.core.scanner.MediaScanner
import eu.darken.sdmse.squeezer.core.tasks.SqueezerProcessTask
import eu.darken.sdmse.squeezer.core.tasks.SqueezerScanTask
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue
import java.time.Duration
import java.time.Instant
import javax.inject.Provider

class SqueezerTest : BaseTest() {

    // ─────────────────────────── data / model helpers ───────────────────────────

    private fun createImage(
        path: String,
        size: Long = 1024 * 1024L,
        estimatedCompressedSize: Long? = null,
        wasCompressedBefore: Boolean = false,
    ) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_JPEG,
        estimatedCompressedSize = estimatedCompressedSize,
        wasCompressedBefore = wasCompressedBefore,
    )

    private fun createWebp(path: String, size: Long = 1024 * 1024L) = CompressibleImage(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleImage.MIME_TYPE_WEBP,
    )

    private fun createVideo(path: String, size: Long = 2 * 1024 * 1024L) = CompressibleVideo(
        lookup = LocalPathLookup(
            lookedUp = LocalPath(File(path)),
            fileType = FileType.FILE,
            size = size,
            modifiedAt = Instant.EPOCH,
            target = null,
        ),
        mimeType = CompressibleVideo.MIME_TYPE_MP4,
        durationMs = 10_000L,
        bitrateBps = 2_000_000L,
    )

    @Test
    fun `totalSize - calculates sum of all image sizes`() {
        val data = Squeezer.Data(
            media = setOf(
                createImage("img1.jpg", size = 1000L),
                createImage("img2.jpg", size = 2000L),
                createImage("img3.jpg", size = 3000L),
            )
        )

        data.totalSize shouldBe 6000L
    }

    @Test
    fun `totalSize - empty set returns zero`() {
        val data = Squeezer.Data(media = emptySet())
        data.totalSize shouldBe 0L
    }

    @Test
    fun `totalCount - returns correct count`() {
        val data = Squeezer.Data(
            media = setOf(
                createImage("img1.jpg"),
                createImage("img2.jpg"),
            )
        )

        data.totalCount shouldBe 2
    }

    @Test
    fun `estimatedSavings - calculates sum of all estimated savings`() {
        val data = Squeezer.Data(
            media = setOf(
                createImage("img1.jpg", size = 1000L, estimatedCompressedSize = 800L),
                createImage("img2.jpg", size = 2000L, estimatedCompressedSize = 1500L),
                createImage("img3.jpg", size = 3000L, estimatedCompressedSize = 2000L),
            )
        )

        // Savings: (1000-800) + (2000-1500) + (3000-2000) = 200 + 500 + 1000 = 1700
        data.estimatedSavings shouldBe 1700L
    }

    @Test
    fun `estimatedSavings - handles null estimated sizes`() {
        val data = Squeezer.Data(
            media = setOf(
                createImage("img1.jpg", size = 1000L, estimatedCompressedSize = 800L),
                createImage("img2.jpg", size = 2000L, estimatedCompressedSize = null),
            )
        )

        // Only img1 has estimated savings: 1000-800 = 200
        data.estimatedSavings shouldBe 200L
    }

    @Test
    fun `prune - removes processed images`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")
        val img3 = createImage("img3.jpg")

        val original = Squeezer.Data(media = setOf(img1, img2, img3))

        val processedIds = setOf(img1.identifier, img3.identifier)

        val pruned = original.prune(processedIds)

        pruned.images.size shouldBe 1
        pruned.images.first().identifier shouldBe img2.identifier
    }

    @Test
    fun `prune - removes all when all processed`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")

        val original = Squeezer.Data(media = setOf(img1, img2))

        val processedIds = setOf(img1.identifier, img2.identifier)

        val pruned = original.prune(processedIds)

        pruned.images shouldBe emptySet()
    }

    @Test
    fun `prune - handles empty processed set`() {
        val img1 = createImage("img1.jpg")
        val img2 = createImage("img2.jpg")

        val original = Squeezer.Data(media = setOf(img1, img2))

        val pruned = original.prune(emptySet())

        pruned.images.size shouldBe 2
    }

    @Test
    fun `CompressibleImage - estimatedSavings calculation`() {
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = 650L)

        image.estimatedSavings shouldBe 350L
    }

    @Test
    fun `CompressibleImage - estimatedSavings with null compressed size`() {
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = null)

        image.estimatedSavings shouldBe null
    }

    @Test
    fun `CompressibleImage - estimatedSavings coerces to at least zero`() {
        // Edge case: estimated compressed size somehow larger than original
        val image = createImage("test.jpg", size = 1000L, estimatedCompressedSize = 1500L)

        image.estimatedSavings shouldBe 0L
    }

    @Test
    fun `CompressibleImage - isJpeg detection`() {
        val jpegImage = createImage("test.jpg")

        jpegImage.isJpeg shouldBe true
        jpegImage.isWebp shouldBe false
    }

    @Test
    fun `CompressibleImage - isWebp detection`() {
        val webpImage = createWebp("test.webp")

        webpImage.isJpeg shouldBe false
        webpImage.isWebp shouldBe true
    }

    // ─────────────────────────── workflow harness ───────────────────────────

    // `submit()` wraps work in `keepResourceHoldersAlive(gatewaySwitch)`, which calls
    // `addChild(sharedResource)` + `sharedResource.get()` on the gateway. Plain MockK mocks
    // would fail at those calls — so we wire `gatewaySwitch.sharedResource` to a real
    // `SharedResource.createKeepAlive(...)` backed by a long-lived scope.
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun stopKeepAliveScope() {
        keepAliveScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private class Setup(
        val squeezer: Squeezer,
        val scanner: MediaScanner,
        val imageProcessor: ImageProcessor,
        val videoProcessor: VideoProcessor,
        val exclusionManager: ExclusionManager,
        val settings: SqueezerSettings,
    )

    private fun setup(
        scanPaths: Set<eu.darken.sdmse.common.files.APath> = setOf(LocalPath.build("/dcim")),
        includeJpeg: Boolean = true,
        includeWebp: Boolean = true,
        includeVideo: Boolean = true,
        includeLossyAuxImages: Boolean = false,
        minSizeBytes: Long = SqueezerSettings.MIN_FILE_SIZE,
        minAge: Duration = Duration.ofDays(0),
        skipPreviouslyCompressed: Boolean = false,
        compressionQuality: Int = 80,
    ): Setup {
        val gatewaySwitch = mockk<GatewaySwitch>().apply {
            every { sharedResource } returns SharedResource.createKeepAlive("gw", keepAliveScope)
        }
        val scanner = mockk<MediaScanner>(relaxed = true).apply {
            every { progress } returns emptyFlow()
        }
        val imageProcessor = mockk<ImageProcessor>(relaxed = true).apply {
            every { progress } returns emptyFlow()
        }
        val videoProcessor = mockk<VideoProcessor>(relaxed = true).apply {
            every { progress } returns emptyFlow()
        }
        val exclusionManager = mockk<ExclusionManager>().apply {
            // Default: save() echoes its input back — distinct ids per call.
            coEvery { save(any()) } answers { firstArg<Set<Exclusion>>().toList() }
        }
        val settings = mockk<SqueezerSettings>().apply {
            every { this@apply.scanPaths } returns mockDataStoreValue(SqueezerSettings.ScanPaths(paths = scanPaths))
            every { this@apply.minSizeBytes } returns mockDataStoreValue(minSizeBytes)
            every { this@apply.minAge } returns mockDataStoreValue(minAge)
            every { this@apply.compressionQuality } returns mockDataStoreValue(compressionQuality)
            every { this@apply.includeJpeg } returns mockDataStoreValue(includeJpeg)
            every { this@apply.includeWebp } returns mockDataStoreValue(includeWebp)
            every { this@apply.includeVideo } returns mockDataStoreValue(includeVideo)
            every { this@apply.includeLossyAuxImages } returns mockDataStoreValue(includeLossyAuxImages)
            every { this@apply.skipPreviouslyCompressed } returns mockDataStoreValue(skipPreviouslyCompressed)
        }

        val squeezer = Squeezer(
            appScope = keepAliveScope,
            gatewaySwitch = gatewaySwitch,
            exclusionManager = exclusionManager,
            scanner = Provider { scanner },
            imageProcessor = Provider { imageProcessor },
            videoProcessor = Provider { videoProcessor },
            settings = settings,
        )
        return Setup(squeezer, scanner, imageProcessor, videoProcessor, exclusionManager, settings)
    }

    private suspend fun Squeezer.dataFromState(): Squeezer.Data? = state.map { it.data }.first()
    private suspend fun Squeezer.lastResultFromState(): eu.darken.sdmse.squeezer.core.tasks.SqueezerTask.Result? =
        state.map { it.lastResult }.first()

    private fun scanResult(
        items: Set<CompressibleMedia> = emptySet(),
        skipped: Int = 0,
    ): MediaScanner.ScanResult = MediaScanner.ScanResult(
        items = items,
        skippedInaccessibleCount = skipped,
    )

    // ─────────────────────────── scan ───────────────────────────

    @Test
    fun `submit ScanTask with no media found yields empty Success`() = runTest2 {
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult()

        val result = s.squeezer.submit(SqueezerScanTask())

        result.shouldBeInstanceOf<SqueezerScanTask.Success>()
        s.squeezer.dataFromState() shouldBe Squeezer.Data(media = emptySet())
    }

    @Test
    fun `submit ScanTask populates internalData with media`() = runTest2 {
        val a = createImage("a.jpg", size = 1000L)
        val b = createImage("b.jpg", size = 2000L)
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b))

        s.squeezer.submit(SqueezerScanTask())

        s.squeezer.dataFromState()!!.media shouldContainExactlyInAnyOrder setOf(a, b)
    }

    @Test
    fun `submit ScanTask task-paths override settings paths`() = runTest2 {
        val taskPath = LocalPath.build("/override")
        val settingsPath = LocalPath.build("/from-settings")
        val s = setup(scanPaths = setOf(settingsPath))
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask(paths = setOf(taskPath)))

        captured.captured.paths shouldBe setOf(taskPath)
    }

    @Test
    fun `submit ScanTask without task-paths falls back to settings paths`() = runTest2 {
        val settingsPath = LocalPath.build("/from-settings")
        val s = setup(scanPaths = setOf(settingsPath))
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.paths shouldBe setOf(settingsPath)
    }

    @Test
    fun `submit ScanTask builds enabledMimeTypes from settings - all enabled`() = runTest2 {
        val s = setup(includeJpeg = true, includeWebp = true, includeVideo = true)
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.enabledMimeTypes shouldBe setOf(
            CompressibleImage.MIME_TYPE_JPEG,
            CompressibleImage.MIME_TYPE_WEBP,
            CompressibleVideo.MIME_TYPE_MP4,
        )
    }

    @Test
    fun `submit ScanTask builds enabledMimeTypes from settings - jpeg only`() = runTest2 {
        val s = setup(includeJpeg = true, includeWebp = false, includeVideo = false)
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.enabledMimeTypes shouldBe setOf(CompressibleImage.MIME_TYPE_JPEG)
    }

    @Test
    fun `submit ScanTask builds empty enabledMimeTypes when all types disabled`() = runTest2 {
        // Edge case: all three switches off. Squeezer doesn't currently block this — the scan
        // still runs but with an empty MIME set, so MediaScanner returns nothing. Documents the
        // intentional permissive behaviour; a regression introducing a require() would be
        // detected here.
        val s = setup(includeJpeg = false, includeWebp = false, includeVideo = false)
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.enabledMimeTypes shouldBe emptySet()
    }

    @Test
    fun `submit ScanTask threads scan options from settings`() = runTest2 {
        val s = setup(
            minSizeBytes = 4096L,
            minAge = Duration.ofDays(30),
            skipPreviouslyCompressed = true,
            compressionQuality = 65,
        )
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.minimumSize shouldBe 4096L
        captured.captured.minAge shouldBe Duration.ofDays(30)
        captured.captured.skipPreviouslyCompressed shouldBe true
        captured.captured.compressionQuality shouldBe 65
        captured.captured.includeLossyAuxImages shouldBe false
    }

    @Test
    fun `submit ScanTask threads includeLossyAuxImages opt-in from settings`() = runTest2 {
        val s = setup(includeLossyAuxImages = true)
        val captured = slot<MediaScanner.Options>()
        coEvery { s.scanner.scan(capture(captured)) } returns scanResult()

        s.squeezer.submit(SqueezerScanTask())

        captured.captured.includeLossyAuxImages shouldBe true
    }

    @Test
    fun `submit ScanTask Data totals are derived from scanned media`() = runTest2 {
        val a = createImage("a.jpg", size = 1000L, estimatedCompressedSize = 600L)
        val b = createImage("b.jpg", size = 3000L, estimatedCompressedSize = 2000L)
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b), skipped = 5)

        s.squeezer.submit(SqueezerScanTask())

        // SqueezerScanTask.Success fields are private; assert via Data instead. Together they
        // tell the same story: any drift between Success and Data would surface here.
        val data = s.squeezer.dataFromState()!!
        data.totalSize shouldBe 4000L
        data.estimatedSavings shouldBe 1400L  // 400 + 1000
        data.totalCount shouldBe 2
    }

    @Test
    fun `submit ScanTask updates lastResult on success`() = runTest2 {
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult()

        val result = s.squeezer.submit(SqueezerScanTask())

        s.squeezer.lastResultFromState() shouldBe result
    }

    // ─────────────────────────── process ───────────────────────────

    @Test
    fun `submit ProcessTask throws IllegalStateException when no scan data`() = runTest2 {
        val s = setup()

        shouldThrow<IllegalStateException> {
            s.squeezer.submit(SqueezerProcessTask())
        }
    }

    @Test
    fun `submit ProcessTask with TargetMode All processes every media in snapshot`() = runTest2 {
        val a = createImage("a.jpg")
        val b = createImage("b.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b))
        s.squeezer.submit(SqueezerScanTask())

        val capturedTargets = slot<Set<CompressibleImage>>()
        coEvery { s.imageProcessor.process(capture(capturedTargets), any()) } returns
            ImageProcessor.Result(success = setOf(a, b), failed = emptyMap(), savedSpace = 400L)

        s.squeezer.submit(SqueezerProcessTask(mode = SqueezerProcessTask.TargetMode.All()))

        capturedTargets.captured shouldBe setOf(a, b)
    }

    @Test
    fun `submit ProcessTask with TargetMode Selected filters to chosen ids`() = runTest2 {
        val keep = createImage("keep.jpg")
        val skip = createImage("skip.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(keep, skip))
        s.squeezer.submit(SqueezerScanTask())

        val capturedTargets = slot<Set<CompressibleImage>>()
        coEvery { s.imageProcessor.process(capture(capturedTargets), any()) } returns
            ImageProcessor.Result(success = setOf(keep), failed = emptyMap(), savedSpace = 100L)

        s.squeezer.submit(
            SqueezerProcessTask(mode = SqueezerProcessTask.TargetMode.Selected(setOf(keep.identifier))),
        )

        capturedTargets.captured shouldBe setOf(keep)
    }

    @Test
    fun `submit ProcessTask with stale Selected id silently drops it`() = runTest2 {
        // Defends the snapshot-filter at performProcess: a caller passing an id not present in
        // internalData (e.g. an item that got excluded between scan and process) must not crash
        // and must not be forwarded to the processor.
        val real = createImage("real.jpg")
        val staleId = CompressibleMedia.Id(LocalPath.build("/stale").path)
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(real))
        s.squeezer.submit(SqueezerScanTask())

        val capturedTargets = slot<Set<CompressibleImage>>()
        coEvery { s.imageProcessor.process(capture(capturedTargets), any()) } returns
            ImageProcessor.Result(success = setOf(real), failed = emptyMap(), savedSpace = 0L)

        s.squeezer.submit(
            SqueezerProcessTask(
                mode = SqueezerProcessTask.TargetMode.Selected(setOf(real.identifier, staleId)),
            ),
        )

        capturedTargets.captured shouldBe setOf(real)
    }

    @Test
    fun `submit ProcessTask routes images and videos to their respective processors`() = runTest2 {
        val img = createImage("a.jpg")
        val vid = createVideo("b.mp4")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(img, vid))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.imageProcessor.process(any(), any()) } returns
            ImageProcessor.Result(success = setOf(img), failed = emptyMap(), savedSpace = 100L)
        coEvery { s.videoProcessor.process(any(), any()) } returns
            VideoProcessor.Result(success = setOf(vid), failed = emptyMap(), savedSpace = 200L)

        s.squeezer.submit(SqueezerProcessTask())

        coVerify(exactly = 1) { s.imageProcessor.process(setOf(img), any()) }
        coVerify(exactly = 1) { s.videoProcessor.process(setOf(vid), any()) }
    }

    @Test
    fun `submit ProcessTask skips image processor when no image targets`() = runTest2 {
        val vid = createVideo("b.mp4")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(vid))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.videoProcessor.process(any(), any()) } returns
            VideoProcessor.Result(success = setOf(vid), failed = emptyMap(), savedSpace = 200L)

        s.squeezer.submit(SqueezerProcessTask())

        // No image targets → image processor must not be invoked. Pin the short-circuit (line
        // 162 in Squeezer.kt) — a regression that always called process(emptySet()) would still
        // be technically correct but burns the withProgress() scaffolding for nothing.
        coVerify(exactly = 0) { s.imageProcessor.process(any(), any()) }
    }

    @Test
    fun `submit ProcessTask skips video processor when no video targets`() = runTest2 {
        val img = createImage("a.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(img))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.imageProcessor.process(any(), any()) } returns
            ImageProcessor.Result(success = setOf(img), failed = emptyMap(), savedSpace = 100L)

        s.squeezer.submit(SqueezerProcessTask())

        coVerify(exactly = 0) { s.videoProcessor.process(any(), any()) }
    }

    @Test
    fun `submit ProcessTask prunes successfully processed items from internalData`() = runTest2 {
        val processed = createImage("processed.jpg")
        val keep = createImage("keep.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(processed, keep))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.imageProcessor.process(any(), any()) } returns
            ImageProcessor.Result(success = setOf(processed), failed = emptyMap(), savedSpace = 100L)

        s.squeezer.submit(
            SqueezerProcessTask(mode = SqueezerProcessTask.TargetMode.Selected(setOf(processed.identifier))),
        )

        s.squeezer.dataFromState()!!.media shouldBe setOf(keep)
    }

    @Test
    fun `submit ProcessTask retains failed items in internalData`() = runTest2 {
        // A processor failure must NOT prune the item — the user should still see it in the
        // list so they can retry or exclude. Pruning happens off `success`, not off `targets`;
        // a regression that confused the two would fail here.
        val failed = createImage("failed.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(failed))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.imageProcessor.process(any(), any()) } returns ImageProcessor.Result(
            success = emptySet(),
            failed = mapOf(failed to IllegalStateException("nope")),
            savedSpace = 0L,
        )

        s.squeezer.submit(
            SqueezerProcessTask(mode = SqueezerProcessTask.TargetMode.Selected(setOf(failed.identifier))),
        )

        s.squeezer.dataFromState()!!.media shouldBe setOf(failed)
    }

    @Test
    fun `submit ProcessTask Success aggregates affectedSpace and affectedPaths across processors`() = runTest2 {
        val img = createImage("a.jpg")
        val vid = createVideo("b.mp4")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(img, vid))
        s.squeezer.submit(SqueezerScanTask())

        coEvery { s.imageProcessor.process(any(), any()) } returns
            ImageProcessor.Result(success = setOf(img), failed = emptyMap(), savedSpace = 100L)
        coEvery { s.videoProcessor.process(any(), any()) } returns
            VideoProcessor.Result(success = setOf(vid), failed = emptyMap(), savedSpace = 200L)

        val result = s.squeezer.submit(SqueezerProcessTask()) as SqueezerProcessTask.Success

        result.affectedSpace shouldBe 300L
        result.affectedPaths shouldBe setOf(img.path, vid.path)
        result.processedCount shouldBe 2
        result.failedCount shouldBe 0
        result.failureReasons shouldBe emptyMap()
    }

    @Test
    fun `submit ProcessTask Success groups failures by FailureReason`() = runTest2 {
        val a = createImage("a.jpg")
        val b = createImage("b.jpg")
        val c = createImage("c.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b, c))
        s.squeezer.submit(SqueezerScanTask())

        // Two IOExceptions → one FailureReason bucket; one UnsupportedFormatException → another.
        // Catches a regression that grouped by Throwable identity instead of via toFailureReason().
        coEvery { s.imageProcessor.process(any(), any()) } returns ImageProcessor.Result(
            success = emptySet(),
            failed = mapOf(
                a to java.io.IOException("io a"),
                b to java.io.IOException("io b"),
                c to UnsupportedFormatException("codec"),
            ),
            savedSpace = 0L,
        )

        val result = s.squeezer.submit(SqueezerProcessTask()) as SqueezerProcessTask.Success

        result.failedCount shouldBe 3
        result.failureReasons[FailureReason.IO_ERROR] shouldBe 2
        result.failureReasons[FailureReason.CODEC_UNSUPPORTED] shouldBe 1
    }

    @Test
    fun `submit ProcessTask uses qualityOverride when provided`() = runTest2 {
        val img = createImage("a.jpg")
        val s = setup(compressionQuality = 80)
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(img))
        s.squeezer.submit(SqueezerScanTask())

        val capturedQuality = slot<Int>()
        coEvery { s.imageProcessor.process(any(), capture(capturedQuality)) } returns
            ImageProcessor.Result(success = setOf(img), failed = emptyMap(), savedSpace = 100L)

        s.squeezer.submit(SqueezerProcessTask(qualityOverride = 50))

        capturedQuality.captured shouldBe 50
    }

    @Test
    fun `submit ProcessTask falls back to settings quality when no override`() = runTest2 {
        val img = createImage("a.jpg")
        val s = setup(compressionQuality = 65)
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(img))
        s.squeezer.submit(SqueezerScanTask())

        val capturedQuality = slot<Int>()
        coEvery { s.imageProcessor.process(any(), capture(capturedQuality)) } returns
            ImageProcessor.Result(success = setOf(img), failed = emptyMap(), savedSpace = 100L)

        s.squeezer.submit(SqueezerProcessTask(qualityOverride = null))

        capturedQuality.captured shouldBe 65
    }

    // ─────────────────────────── exclude ───────────────────────────

    @Test
    fun `exclude with no scan data returns empty set`() = runTest2 {
        val s = setup()

        s.squeezer.exclude(setOf(createImage("a.jpg").identifier)) shouldBe emptySet()
        coVerify(exactly = 0) { s.exclusionManager.save(any()) }
    }

    @Test
    fun `exclude saves PathExclusion with SQUEEZER tag for each identifier`() = runTest2 {
        val a = createImage("/storage/a.jpg")
        val b = createImage("/storage/b.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b))
        s.squeezer.submit(SqueezerScanTask())

        val captured = slot<Set<Exclusion>>()
        coEvery { s.exclusionManager.save(capture(captured)) } answers { firstArg<Set<Exclusion>>().toList() }

        s.squeezer.exclude(setOf(a.identifier, b.identifier))

        captured.captured.size shouldBe 2
        captured.captured.all { it is PathExclusion } shouldBe true
        captured.captured.all { Exclusion.Tag.SQUEEZER in it.tags } shouldBe true
        captured.captured.map { (it as PathExclusion).path }.toSet() shouldBe setOf(a.path, b.path)
    }

    @Test
    fun `exclude removes excluded media from internalData`() = runTest2 {
        val gone = createImage("/storage/gone.jpg")
        val stay = createImage("/storage/stay.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(gone, stay))
        s.squeezer.submit(SqueezerScanTask())

        s.squeezer.exclude(setOf(gone.identifier))

        s.squeezer.dataFromState()!!.media shouldBe setOf(stay)
    }

    @Test
    fun `exclude returns saved-exclusion ids from ExclusionManager save not the requested ids`() = runTest2 {
        // Regression test for what used to be FIXME(squeezer-list-exclusion-count): the VM used
        // `ids.size` as the snackbar count, while `ExclusionManager.save()` filters out
        // duplicates already in the persisted set. Now `Squeezer.exclude` returns the actual
        // saved ids and the VM reads .size off that. Fixture: save() answers with a smaller
        // set than was requested.
        val a = createImage("/storage/a.jpg")
        val b = createImage("/storage/b.jpg")
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a, b))
        s.squeezer.submit(SqueezerScanTask())

        // Both selected but save() pretends one was already excluded — only one Exclusion comes
        // back from save(). With the bug the VM would still say "2 excluded".
        val onlyOne = PathExclusion(
            path = a.path,
            tags = setOf(Exclusion.Tag.SQUEEZER),
        )
        coEvery { s.exclusionManager.save(any()) } returns listOf(onlyOne)

        val saved = s.squeezer.exclude(setOf(a.identifier, b.identifier))

        saved shouldBe setOf(onlyOne.id)
        saved.size shouldBe 1
    }

    @Test
    fun `exclude with unknown identifier silently drops it`() = runTest2 {
        val a = createImage("/storage/a.jpg")
        val unknownId = CompressibleMedia.Id(LocalPath.build("/storage/unknown").path)
        val s = setup()
        coEvery { s.scanner.scan(any()) } returns scanResult(items = setOf(a))
        s.squeezer.submit(SqueezerScanTask())

        val captured = slot<Set<Exclusion>>()
        coEvery { s.exclusionManager.save(capture(captured)) } answers { firstArg<Set<Exclusion>>().toList() }

        s.squeezer.exclude(setOf(a.identifier, unknownId))

        // Only `a`'s path is saved — the unknown id never resolves to a media so it's filtered
        // out by `mapNotNull`.
        captured.captured.size shouldBe 1
        (captured.captured.single() as PathExclusion).path shouldBe a.path
    }
}
