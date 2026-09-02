package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.scanner.LossyAuxDetector
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.ImageContentHasher
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.mockDataStoreValue
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ImageProcessorTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val imageCompressor = mockk<ImageCompressor>(relaxed = true)
    private val historyDatabase = mockk<CompressionHistoryDatabase>(relaxed = true)
    private val imageContentHasher = mockk<ImageContentHasher>()
    private val fileTransaction = mockk<FileTransaction>()
    private val lossyAuxDetector = mockk<LossyAuxDetector>(relaxed = true)
    private val settings = mockk<SqueezerSettings>()

    private lateinit var subject: ImageProcessor

    @Before
    fun setup() {
        every { settings.writeExifMarker } returns mockDataStoreValue(false)
        // Opt-in ON => the HDR/depth preflight is a no-op, so existing compression cases are unaffected.
        every { settings.includeLossyAuxImages } returns mockDataStoreValue(true)
        every { settings.includeMotionPhotos } returns mockDataStoreValue(true)
        every { settings.includeOversizedImages } returns mockDataStoreValue(true)

        subject = ImageProcessor(
            context = RuntimeEnvironment.getApplication(),
            imageCompressor = imageCompressor,
            dispatcherProvider = TestDispatcherProvider(),
            historyDatabase = historyDatabase,
            imageContentHasher = imageContentHasher,
            fileTransaction = fileTransaction,
            lossyAuxDetector = lossyAuxDetector,
            settings = settings,
        )
    }

    private fun createImage(
        path: String? = null,
        size: Long = 5_000_000L,
    ): CompressibleImage {
        val filePath = path ?: java.io.File(tempFolder.root, "test.jpg").apply {
            writeBytes(ByteArray(size.toInt()))
        }.absolutePath

        return CompressibleImage(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(filePath)),
                fileType = FileType.FILE,
                size = size,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleImage.MIME_TYPE_JPEG,
        )
    }

    @Test
    fun `process - successful compression`() = runTest {
        val image = createImage()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 5_000_000L,
                replacementSize = 2_000_000L,
                savedBytes = 3_000_000L,
                replaced = true,
            )
        }

        val hashId = ContentId("img-hash")
        coEvery { imageContentHasher.computeHash(any()) } returns ContentIdentifier.ImageHash(hashId)

        val result = subject.process(setOf(image), quality = 80)

        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 3_000_000L

        coVerify { historyDatabase.recordCompression(hashId) }
    }

    @Test
    fun `process - no savings recorded in history`() = runTest {
        val image = createImage()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 5_000_000L,
                replacementSize = 5_500_000L,
                savedBytes = 0L,
                replaced = false,
            )
        }

        val hashId = ContentId("img-hash")
        coEvery { imageContentHasher.computeHash(any()) } returns ContentIdentifier.ImageHash(hashId)

        val result = subject.process(setOf(image), quality = 80)

        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 0L

        coVerify { historyDatabase.recordNoSavings(hashId) }
    }

    @Test
    fun `process - HDR-depth photo is preserved (skipped, not compressed) when opt-in is off`() = runTest {
        val image = createImage()
        every { settings.includeLossyAuxImages } returns mockDataStoreValue(false)
        every { lossyAuxDetector.hasLossyAux(any(), any()) } returns true

        val result = subject.process(setOf(image), quality = 80)

        result.skippedGuarded.size shouldBe 1
        result.success.size shouldBe 0
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 0L
        coVerify(exactly = 0) { fileTransaction.replace(any(), any(), any()) }
    }

    @Test
    fun `process - Motion Photo is preserved (skipped, not compressed) when opt-in is off`() = runTest {
        val image = createImage()
        every { settings.includeMotionPhotos } returns mockDataStoreValue(false)
        every { lossyAuxDetector.hasMotionVideo(any(), any()) } returns true

        val result = subject.process(setOf(image), quality = 80)

        result.skippedGuarded.size shouldBe 1
        result.success.size shouldBe 0
        coVerify(exactly = 0) { fileTransaction.replace(any(), any(), any()) }
    }

    @Test
    fun `process - oversized image is preserved when opt-in was turned off after the scan`() = runTest {
        val image = createImage().copy(willDownscale = true)
        every { settings.includeOversizedImages } returns mockDataStoreValue(false)

        val result = subject.process(setOf(image), quality = 80)

        result.skippedGuarded.size shouldBe 1
        coVerify(exactly = 0) { fileTransaction.replace(any(), any(), any()) }
    }

    @Test
    fun `process - history write failure does not mark as failed`() = runTest {
        val image = createImage()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 5_000_000L,
                replacementSize = 2_000_000L,
                savedBytes = 3_000_000L,
                replaced = true,
            )
        }

        coEvery { imageContentHasher.computeHash(any()) } throws RuntimeException("DB write failed")

        val result = subject.process(setOf(image), quality = 80)

        // Bug fix validation: item should be in success only, NOT in failed
        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 3_000_000L
    }

    @Test
    fun `process - eligibility failure adds to failed`() = runTest {
        val image = createImage(path = "/nonexistent/image.jpg")

        val result = subject.process(setOf(image), quality = 80)

        result.success.size shouldBe 0
        result.failed.size shouldBe 1
    }

    @Test
    fun `process - empty targets returns empty result`() = runTest {
        val result = subject.process(emptySet(), quality = 80)

        result.success shouldBe emptySet()
        result.failed shouldBe emptyMap()
        result.savedSpace shouldBe 0L
    }

    /**
     * The published progress. [ImageProcessor.progress] is throttled, so collecting it drops exactly
     * the intermediate states these tests are about; [Progress.Client.updateProgress] reads the same
     * state synchronously and without side effects.
     */
    private fun ImageProcessor.currentProgress(): Progress.Data? {
        var snapshot: Progress.Data? = null
        updateProgress { snapshot = it; it }
        return snapshot
    }

    @Test
    fun `process - the state published before the first item keeps the batch counter`() = runTest {
        // No targets, so the pre-loop publish is the only state: the run's starting position inside
        // the batch. An Indeterminate here would drop the card's ring from "1 of 3" to spinning.
        subject.process(emptySet(), quality = 80, itemOffset = 1, itemTotal = 3)

        subject.currentProgress()!!.let {
            it.count.current shouldBe 1L
            it.count.max shouldBe 3L
            it.subCount shouldBe null
            it.primary.get(RuntimeEnvironment.getApplication()) shouldBe "Preparing"
        }
    }

    @Test
    fun `process - the item counter reaches the last item`() = runTest {
        val first = createImage()
        val secondPath = java.io.File(tempFolder.root, "second.jpg").apply {
            writeBytes(ByteArray(5_000_000))
        }.absolutePath
        val second = createImage(path = secondPath)

        // Sampled while an item is in flight.
        var inFlight: Progress.Data? = null
        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            inFlight = subject.currentProgress()
            FileTransaction.Outcome(
                originalSize = 5_000_000L,
                replacementSize = 2_000_000L,
                savedBytes = 3_000_000L,
                replaced = true,
            )
        }
        coEvery { imageContentHasher.computeHash(any()) } returns ContentIdentifier.ImageHash(ContentId("img"))

        subject.process(setOf(first, second), quality = 80)

        // There is no per-image progress, so the inner ring spins for the duration of each item.
        inFlight!!.subCount shouldBe Progress.Count.Indeterminate()

        subject.currentProgress()!!.let {
            it.count.current shouldBe 2L
            it.count.max shouldBe 2L
        }
    }

    @Test
    fun `process - a preserved HDR-depth photo still advances the counter`() = runTest {
        val image = createImage()
        every { settings.includeLossyAuxImages } returns mockDataStoreValue(false)
        every { lossyAuxDetector.hasLossyAux(any(), any()) } returns true

        subject.process(setOf(image), quality = 80)

        subject.currentProgress()!!.let {
            it.count.current shouldBe 1L
            it.count.max shouldBe 1L
        }
    }
}
