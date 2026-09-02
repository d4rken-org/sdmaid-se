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
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import eu.darken.sdmse.squeezer.core.PriorCompression
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryDatabase
import eu.darken.sdmse.squeezer.core.history.CompressionHistoryEntity
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
import testhelpers.mockDataStoreValue
import java.io.File
import java.time.Instant

/**
 * Covers the per-file facts the scanner attaches to results so the list rows can mark them:
 * [PriorCompression] from the compression history / EXIF marker, [CompressibleImage.hasLossyAux],
 * [CompressibleImage.hasMotionVideo] and [CompressibleImage.willDownscale].
 */
class MediaScannerChipStateTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "MediaScannerChipStateTest")

    private val exclusionManager: ExclusionManager = mockk(relaxed = true)
    private val localGateway: LocalGateway = mockk(relaxed = true)
    private val mimeTypeTool: MimeTypeTool = mockk()
    private val historyDatabase: CompressionHistoryDatabase = mockk(relaxed = true)
    private val imageContentHasher: ImageContentHasher = mockk(relaxed = true)
    private val videoContentHasher: VideoContentHasher = mockk(relaxed = true)
    private val compressionEstimator: CompressionEstimator = mockk(relaxed = true)
    private val exifPreserver: ExifPreserver = mockk(relaxed = true)
    private val lossyAuxDetector: LossyAuxDetector = mockk(relaxed = true)
    private val dimensionProbe: ImageDimensionProbe = mockk(relaxed = true)
    private val settings: SqueezerSettings = mockk(relaxed = true)

    private val dispatcherProvider: DispatcherProvider = TestDispatcherProvider()

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()

        every { exclusionManager.exclusions } returns flowOf(emptyList<ExclusionHolder>())
        coEvery { mimeTypeTool.determineMimeType(any<APathLookup<*>>()) } returns CompressibleImage.MIME_TYPE_JPEG
        coEvery { imageContentHasher.computeHash(any()) } returns ContentIdentifier.ImageHash(ContentId("hash"))
        coEvery { historyDatabase.getOutcome(any()) } returns null
        every { settings.writeExifMarker } returns mockDataStoreValue(false)
        every { exifPreserver.hasCompressionMarker(any()) } returns false
        every { lossyAuxDetector.hasLossyAux(any(), any()) } returns false
        every { lossyAuxDetector.hasMotionVideo(any(), any()) } returns false
        every { dimensionProbe.read(any()) } returns null
    }

    @AfterEach
    fun teardown() {
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
        lossyAuxDetector = lossyAuxDetector,
        dimensionProbe = dimensionProbe,
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

    private fun photo(name: String): LocalPathLookup {
        val file = File(testDir, name).apply { writeBytes(ByteArray(256)) }
        return lookupFor(file)
    }

    @Test
    fun `skip off - COMPRESSED history entry surfaces as COMPRESSED`() = runTest {
        stubWalk(photo("done.jpg"))
        coEvery { historyDatabase.getOutcome(any()) } returns CompressionHistoryEntity.Outcome.COMPRESSED

        val result = scanner().scan(options())

        result.items.size shouldBe 1
        result.items.first().priorCompression shouldBe PriorCompression.COMPRESSED
    }

    @Test
    fun `skip off - TRIED_NO_SAVINGS history entry surfaces as NO_SAVINGS`() = runTest {
        stubWalk(photo("nosavings.jpg"))
        coEvery { historyDatabase.getOutcome(any()) } returns CompressionHistoryEntity.Outcome.TRIED_NO_SAVINGS

        val result = scanner().scan(options())

        result.items.size shouldBe 1
        result.items.first().priorCompression shouldBe PriorCompression.NO_SAVINGS
    }

    @Test
    fun `skip off - no history entry leaves priorCompression null`() = runTest {
        stubWalk(photo("fresh.jpg"))

        val result = scanner().scan(options())

        result.items.size shouldBe 1
        result.items.first().priorCompression shouldBe null
    }

    @Test
    fun `skip on - previously compressed item is still dropped`() = runTest {
        stubWalk(photo("done.jpg"))
        coEvery { historyDatabase.getOutcome(any()) } returns CompressionHistoryEntity.Outcome.COMPRESSED

        val result = scanner().scan(options().copy(skipPreviouslyCompressed = true))

        result.items.size shouldBe 0
    }

    @Test
    fun `skip off - EXIF marker without a history entry surfaces as COMPRESSED`() = runTest {
        // The marker is what remains after the history was cleared, so it has to be read even when
        // the skip setting is off.
        stubWalk(photo("marked.jpg"))
        every { settings.writeExifMarker } returns mockDataStoreValue(true)
        every { exifPreserver.hasCompressionMarker(any()) } returns true
        coEvery { historyDatabase.getOutcome(any()) } returns null

        val result = scanner().scan(options())

        result.items.size shouldBe 1
        result.items.first().priorCompression shouldBe PriorCompression.COMPRESSED
    }

    @Test
    fun `HDR-depth opt-in on - item is kept and flagged`() = runTest {
        stubWalk(photo("hdr.jpg"))
        every { lossyAuxDetector.hasLossyAux(any(), any()) } returns true

        val result = scanner().scan(options().copy(includeLossyAuxImages = true))

        result.items.size shouldBe 1
        (result.items.first() as CompressibleImage).hasLossyAux shouldBe true
        result.skippedLossyAuxCount shouldBe 0
    }

    @Test
    fun `HDR-depth opt-in off - item is excluded`() = runTest {
        stubWalk(photo("hdr.jpg"))
        every { lossyAuxDetector.hasLossyAux(any(), any()) } returns true

        val result = scanner().scan(options())

        result.items.size shouldBe 0
        result.skippedLossyAuxCount shouldBe 1
    }

    @Test
    fun `ordinary image carries neither marker`() = runTest {
        stubWalk(photo("plain.jpg"))

        val result = scanner().scan(options())

        result.items.size shouldBe 1
        val image = result.items.first() as CompressibleImage
        image.priorCompression shouldBe null
        image.hasLossyAux shouldBe false
    }

    @Test
    fun `motion photo opt-in on - item is kept and flagged`() = runTest {
        stubWalk(photo("motion.jpg"))
        every { lossyAuxDetector.hasMotionVideo(any(), any()) } returns true

        val result = scanner().scan(options().copy(includeMotionPhotos = true))

        result.items.size shouldBe 1
        (result.items.first() as CompressibleImage).hasMotionVideo shouldBe true
    }

    @Test
    fun `oversized opt-in on - item is kept and flagged as downscaled`() = runTest {
        stubWalk(photo("huge.jpg"))
        every { dimensionProbe.read(any()) } returns ImageDimensionProbe.Dimensions(9000, 6000)

        val result = scanner().scan(options().copy(includeOversizedImages = true))

        (result.items.first() as CompressibleImage).willDownscale shouldBe true
    }

    @Test
    fun `ordinary image - not flagged as downscaled`() = runTest {
        stubWalk(photo("normal.jpg"))
        every { dimensionProbe.read(any()) } returns ImageDimensionProbe.Dimensions(4080, 3072)

        val result = scanner().scan(options())

        (result.items.first() as CompressibleImage).willDownscale shouldBe false
    }

    @Test
    fun `unreadable dimensions - not flagged as downscaled`() = runTest {
        stubWalk(photo("odd.jpg"))
        every { dimensionProbe.read(any()) } returns null

        val result = scanner().scan(options())

        (result.items.first() as CompressibleImage).willDownscale shouldBe false
    }

    @Test
    fun `motion photo opt-in off - item is excluded and counted`() = runTest {
        stubWalk(photo("motion.jpg"))
        every { lossyAuxDetector.hasMotionVideo(any(), any()) } returns true

        val result = scanner().scan(options())

        result.items.size shouldBe 0
        result.skippedMotionPhotoCount shouldBe 1
        result.skippedLossyAuxCount shouldBe 0
    }

    @Test
    fun `oversized opt-in off - item is excluded and counted`() = runTest {
        stubWalk(photo("huge.jpg"))
        every { dimensionProbe.read(any()) } returns ImageDimensionProbe.Dimensions(9000, 6000)

        val result = scanner().scan(options())

        result.items.size shouldBe 0
        result.skippedOversizedCount shouldBe 1
    }

    @Test
    fun `motion photo does not trip the HDR-depth skip`() = runTest {
        // A wrongly routed Motion Photo would land in the HDR count and mislead the result line.
        stubWalk(photo("motion.jpg"))
        every { lossyAuxDetector.hasMotionVideo(any(), any()) } returns true

        val result = scanner().scan(options().copy(includeLossyAuxImages = true))

        result.items.size shouldBe 0
        result.skippedMotionPhotoCount shouldBe 1
        result.skippedLossyAuxCount shouldBe 0
    }
}
