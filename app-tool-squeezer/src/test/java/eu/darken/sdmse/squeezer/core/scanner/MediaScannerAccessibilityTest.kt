package eu.darken.sdmse.squeezer.core.scanner

import eu.darken.sdmse.common.MimeTypeTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.local.LocalPathLookup
import eu.darken.sdmse.exclusion.core.ExclusionManager
import eu.darken.sdmse.exclusion.core.types.ExclusionHolder
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.ImageContentHasher
import eu.darken.sdmse.squeezer.core.history.VideoContentHasher
import eu.darken.sdmse.squeezer.core.processor.ExifPreserver
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.time.Instant

/**
 * Covers the accessibility-gating + skip-counting logic that lives inside [MediaScanner.scan].
 * The actual per-verdict behavior is already in [eu.darken.sdmse.squeezer.core.SqueezerEligibilityTest];
 * this test focuses on the scanner's contract: eligible files pass, ineligible ones increment
 * `skippedInaccessibleCount`, and the result model carries both.
 */
class MediaScannerAccessibilityTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "MediaScannerAccessibilityTest")

    private val exclusionManager: ExclusionManager = mockk(relaxed = true)
    private val localGateway: LocalGateway = mockk(relaxed = true)
    private val mimeTypeTool: MimeTypeTool = mockk()
    private val historyDatabase: CompressionHistoryDatabase = mockk(relaxed = true)
    private val imageContentHasher: ImageContentHasher = mockk(relaxed = true)
    private val videoContentHasher: VideoContentHasher = mockk(relaxed = true)
    private val compressionEstimator: CompressionEstimator = mockk(relaxed = true)
    private val exifPreserver: ExifPreserver = mockk(relaxed = true)
    private val settings: SqueezerSettings = mockk(relaxed = true)

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()

        // ExclusionManager.pathExclusions is a top-level extension that reads `.exclusions.first()`.
        // An empty-emitting flow means the extension returns an empty collection.
        every { exclusionManager.exclusions } returns flowOf(emptyList<ExclusionHolder>())
        // Return a bogus mime type so nothing ever makes it into the results set — we only
        // care about the accessibility filter + skip counter for this test.
        coEvery { mimeTypeTool.determineMimeType(any<APathLookup<*>>()) } returns "application/octet-stream"
    }

    @AfterEach
    fun teardown() {
        testDir.walkTopDown().forEach {
            it.setReadable(true)
            it.setWritable(true)
        }
        testDir.deleteRecursively()
    }

    private fun lookupFor(file: File): LocalPathLookup = LocalPathLookup(
        lookedUp = LocalPath.build(file),
        fileType = FileType.FILE,
        size = if (file.isFile) file.length() else 0L,
        modifiedAt = Instant.ofEpochMilli(file.lastModified().takeIf { it > 0 } ?: 0),
        target = null,
    )

    private fun stubWalk(vararg lookups: LocalPathLookup) {
        val flow: Flow<LocalPathLookup> = flowOf(*lookups)
        coEvery {
            localGateway.walk(
                path = any(),
                options = any(),
                mode = LocalGateway.Mode.NORMAL,
            )
        } returns flow
    }

    private fun scanner() = MediaScanner(
        exclusionManager = exclusionManager,
        dispatcherProvider = dispatcherProvider,
        localGateway = localGateway,
        mimeTypeTool = mimeTypeTool,
        historyDatabase = historyDatabase,
        imageContentHasher = imageContentHasher,
        videoContentHasher = videoContentHasher,
        compressionEstimator = compressionEstimator,
        exifPreserver = exifPreserver,
        settings = settings,
    )

    private fun options(): MediaScanner.Options = MediaScanner.Options(
        paths = setOf(LocalPath.build(testDir)),
        minimumSize = 0L,
        minAge = null,
        enabledMimeTypes = setOf(CompressibleImage.MIME_TYPE_JPEG),
        skipPreviouslyCompressed = false,
        compressionQuality = 80,
    )

    @Test
    fun `eligible file survives accessibility gate`() = runTest {
        val ok = File(testDir, "ok.bin").apply { writeBytes(ByteArray(256)) }
        stubWalk(lookupFor(ok))

        val result = scanner().scan(options())

        // mime filter drops it, but the accessibility gate let it through.
        result.skippedInaccessibleCount shouldBe 0
        result.items.size shouldBe 0
    }

    @Test
    fun `missing file is counted as skipped`() = runTest {
        val ok = File(testDir, "ok.bin").apply { writeBytes(ByteArray(256)) }
        val missing = File(testDir, "missing.bin")
        stubWalk(lookupFor(ok), lookupFor(missing))

        val result = scanner().scan(options())

        result.skippedInaccessibleCount shouldBe 1
    }

    @Test
    fun `multiple ineligible files aggregate in skip counter`() = runTest {
        val okA = File(testDir, "a.bin").apply { writeBytes(ByteArray(256)) }
        val okB = File(testDir, "b.bin").apply { writeBytes(ByteArray(256)) }
        val missingA = File(testDir, "missing-a.bin")
        val missingB = File(testDir, "missing-b.bin")
        stubWalk(lookupFor(okA), lookupFor(missingA), lookupFor(okB), lookupFor(missingB))

        val result = scanner().scan(options())

        result.skippedInaccessibleCount shouldBe 2
    }

    @Test
    fun `empty paths returns empty ScanResult`() = runTest {
        val emptyScanner = scanner()
        val emptyOptions = options().copy(paths = emptySet())

        val result = emptyScanner.scan(emptyOptions)

        result.items.size shouldBe 0
        result.skippedInaccessibleCount shouldBe 0
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"
    }
}
