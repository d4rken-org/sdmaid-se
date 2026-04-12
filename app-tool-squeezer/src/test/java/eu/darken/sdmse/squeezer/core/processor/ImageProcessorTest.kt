package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import eu.darken.sdmse.squeezer.core.SqueezerSettings
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
    private val settings = mockk<SqueezerSettings>()

    private lateinit var subject: ImageProcessor

    @Before
    fun setup() {
        every { settings.writeExifMarker } returns mockDataStoreValue(false)

        subject = ImageProcessor(
            context = RuntimeEnvironment.getApplication(),
            imageCompressor = imageCompressor,
            dispatcherProvider = TestDispatcherProvider(),
            historyDatabase = historyDatabase,
            imageContentHasher = imageContentHasher,
            fileTransaction = fileTransaction,
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
}
