package eu.darken.sdmse.squeezer.core.processor

import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.core.local.File
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class VideoProcessorTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val videoTranscoder = mockk<VideoTranscoder>()
    private val historyDatabase = mockk<CompressionHistoryDatabase>(relaxed = true)
    private val videoContentHasher = mockk<VideoContentHasher>()
    private val fileTransaction = mockk<FileTransaction>()

    private lateinit var subject: VideoProcessor

    @Before
    fun setup() {
        subject = VideoProcessor(
            context = RuntimeEnvironment.getApplication(),
            videoTranscoder = videoTranscoder,
            dispatcherProvider = TestDispatcherProvider(),
            historyDatabase = historyDatabase,
            videoContentHasher = videoContentHasher,
            fileTransaction = fileTransaction,
        )
    }

    private fun createVideo(
        path: String? = null,
        size: Long = 10_000_000L,
        bitrateBps: Long = 5_000_000L,
        durationMs: Long = 60_000L,
    ): CompressibleVideo {
        val filePath = path ?: java.io.File(tempFolder.root, "test.mp4").apply {
            writeBytes(ByteArray(size.toInt()))
        }.absolutePath

        return CompressibleVideo(
            lookup = LocalPathLookup(
                lookedUp = LocalPath(File(filePath)),
                fileType = FileType.FILE,
                size = size,
                modifiedAt = Instant.EPOCH,
                target = null,
            ),
            mimeType = CompressibleVideo.MIME_TYPE_MP4,
            durationMs = durationMs,
            bitrateBps = bitrateBps,
        )
    }

    @Test
    fun `process - successful compression`() = runTest {
        val video = createVideo()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 10_000_000L,
                replacementSize = 5_000_000L,
                savedBytes = 5_000_000L,
                replaced = true,
            )
        }

        val hashId = ContentId("test-hash")
        coEvery { videoContentHasher.computeHash(any()) } returns ContentIdentifier.VideoHash(hashId)

        val result = subject.process(setOf(video), quality = 80)

        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 5_000_000L

        coVerify { historyDatabase.recordCompression(hashId) }
    }

    @Test
    fun `process - no savings recorded in history`() = runTest {
        val video = createVideo()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 10_000_000L,
                replacementSize = 10_500_000L,
                savedBytes = 0L,
                replaced = false,
            )
        }

        val hashId = ContentId("test-hash")
        coEvery { videoContentHasher.computeHash(any()) } returns ContentIdentifier.VideoHash(hashId)

        val result = subject.process(setOf(video), quality = 80)

        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 0L

        coVerify { historyDatabase.recordNoSavings(hashId) }
    }

    @Test
    fun `process - history write failure does not mark as failed`() = runTest {
        val video = createVideo()

        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            FileTransaction.Outcome(
                originalSize = 10_000_000L,
                replacementSize = 5_000_000L,
                savedBytes = 5_000_000L,
                replaced = true,
            )
        }

        coEvery { videoContentHasher.computeHash(any()) } throws RuntimeException("DB write failed")

        val result = subject.process(setOf(video), quality = 80)

        // Bug fix validation: item should be in success only, NOT in failed
        result.success.size shouldBe 1
        result.failed.size shouldBe 0
        result.savedSpace shouldBe 5_000_000L
    }

    @Test
    fun `process - eligibility failure adds to failed`() = runTest {
        // Use a path that doesn't exist — SqueezerEligibility.check will return non-OK
        val video = createVideo(path = "/nonexistent/video.mp4")

        val result = subject.process(setOf(video), quality = 80)

        result.success.size shouldBe 0
        result.failed.size shouldBe 1
    }

    @Test
    fun `process - multiple videos with mixed results`() = runTest {
        val video1 = createVideo()
        val video2Path = java.io.File(tempFolder.root, "test2.mp4").apply {
            writeBytes(ByteArray(5_000_000))
        }.absolutePath
        val video2 = createVideo(path = video2Path, size = 5_000_000L)

        var callCount = 0
        coEvery { fileTransaction.replace(any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) {
                FileTransaction.Outcome(
                    originalSize = 10_000_000L,
                    replacementSize = 5_000_000L,
                    savedBytes = 5_000_000L,
                    replaced = true,
                )
            } else {
                throw RuntimeException("Transcode failed")
            }
        }

        val hashId = ContentId("hash-1")
        coEvery { videoContentHasher.computeHash(any()) } returns ContentIdentifier.VideoHash(hashId)

        val result = subject.process(setOf(video1, video2), quality = 80)

        result.success.size shouldBe 1
        result.failed.size shouldBe 1
        result.savedSpace shouldBe 5_000_000L
    }

    @Test
    fun `process - empty targets returns empty result`() = runTest {
        val result = subject.process(emptySet(), quality = 80)

        result.success shouldBe emptySet()
        result.failed shouldBe emptyMap()
        result.savedSpace shouldBe 0L
    }
}
