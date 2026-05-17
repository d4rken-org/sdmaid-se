package eu.darken.sdmse.squeezer.core.processor

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant

class VideoTimestampPreserverTest : BaseTest() {

    private val testDir = File(IO_TEST_BASEDIR, "VideoTimestampPreserverTest")
    private lateinit var subject: VideoTimestampPreserver

    @BeforeEach
    fun setup() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()
        subject = VideoTimestampPreserver()
    }

    @AfterEach
    fun teardown() {
        testDir.deleteRecursively()
    }

    // ----- parseMetadataDate -----

    @Test
    fun `parseMetadataDate handles AOSP basic format with milliseconds`() {
        val result = subject.parseMetadataDate("20260510T123456.789Z")
        result shouldBe Instant.parse("2026-05-10T12:34:56.789Z")
    }

    @Test
    fun `parseMetadataDate handles AOSP basic format without milliseconds`() {
        val result = subject.parseMetadataDate("20260510T123456Z")
        result shouldBe Instant.parse("2026-05-10T12:34:56Z")
    }

    @Test
    fun `parseMetadataDate returns null for malformed input`() {
        subject.parseMetadataDate("not a date").shouldBeNull()
        subject.parseMetadataDate("2026-05-10T12:34:56Z").shouldBeNull() // extended format, not AOSP basic
        subject.parseMetadataDate("20260510").shouldBeNull() // date-only
    }

    @Test
    fun `parseMetadataDate returns null for null or blank`() {
        subject.parseMetadataDate(null).shouldBeNull()
        subject.parseMetadataDate("").shouldBeNull()
        subject.parseMetadataDate("   ").shouldBeNull()
    }

    // ----- extractFromMvhd: happy paths -----

    @Test
    fun `v0 mvhd with modern post-2026 timestamp survives unsigned-32-bit read`() {
        // 2026-05-10T12:34:56Z → MP4 seconds = 3_861_606_496 (> Int.MAX_VALUE)
        val creation = unixToMp4(1_778_761_696L)
        val modification = creation + 60L
        val file = writeMp4(testDir, "v0_modern.mp4") {
            +ftyp()
            +moov(mvhdV0(creation, modification))
        }

        val result = subject.extractFromMvhd(file)

        result shouldBe VideoTimestampPreserver.TimestampData(creation, modification)
        // Sanity: the unsigned-read trap would have produced a negative value.
        (creation > Int.MAX_VALUE.toLong()) shouldBe true
    }

    @Test
    fun `v1 mvhd with modern timestamp roundtrips`() {
        val creation = unixToMp4(1_778_761_696L)
        val modification = creation + 120L
        val file = writeMp4(testDir, "v1_modern.mp4") {
            +ftyp()
            +moov(mvhdV1(creation, modification))
        }

        val result = subject.extractFromMvhd(file)

        result shouldBe VideoTimestampPreserver.TimestampData(creation, modification)
    }

    @Test
    fun `v1 mvhd with year-2050 timestamp`() {
        // 2050-01-01T00:00:00Z → MP4 seconds = 4_607_452_800 (> 32-bit unsigned max)
        val creation = unixToMp4(2_524_608_000L)
        val modification = creation
        val file = writeMp4(testDir, "v1_2050.mp4") {
            +ftyp()
            +moov(mvhdV1(creation, modification))
        }

        val result = subject.extractFromMvhd(file)

        result shouldBe VideoTimestampPreserver.TimestampData(creation, modification)
    }

    @Test
    fun `moov after mdat is found by walker`() {
        val creation = unixToMp4(1_778_761_696L)
        val file = writeMp4(testDir, "moov_after_mdat.mp4") {
            +ftyp()
            +box("mdat", ByteArray(1024) { it.toByte() })
            +moov(mvhdV0(creation, creation))
        }

        val result = subject.extractFromMvhd(file)

        result shouldBe VideoTimestampPreserver.TimestampData(creation, creation)
    }

    @Test
    fun `top-level largesize moov is parsed correctly`() {
        val creation = unixToMp4(1_778_761_696L)
        val file = writeMp4(testDir, "largesize_moov.mp4") {
            +ftyp()
            +largesizeBox("moov", mvhdV0(creation, creation))
        }

        val result = subject.extractFromMvhd(file)

        result shouldBe VideoTimestampPreserver.TimestampData(creation, creation)
    }

    // ----- extractFromMvhd: rejection paths -----

    @Test
    fun `largesize less than 16 is rejected`() {
        // Manually construct a moov box with size==1 and largesize < 16 (header is 16 bytes).
        val malformedMoov = ByteBuffer.allocate(16).apply {
            putInt(1) // size = 1 → largesize follows
            put("moov".toByteArray(Charsets.US_ASCII))
            putLong(8L) // largesize < 16, malformed
        }.array()
        val file = File(testDir, "bad_largesize.mp4").apply { writeBytes(ftyp() + malformedMoov) }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `box with size less than 8 is rejected`() {
        val bad = ByteBuffer.allocate(8).apply {
            putInt(4) // size < MIN_HEADER_SIZE=8
            put("moov".toByteArray(Charsets.US_ASCII))
        }.array()
        val file = File(testDir, "tiny_box.mp4").apply { writeBytes(ftyp() + bad) }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `child box larger than parent is rejected`() {
        // mvhd claims a size that overruns the moov payload.
        val mvhdHeader = ByteBuffer.allocate(8).apply {
            putInt(9999) // bigger than moov can hold
            put("mvhd".toByteArray(Charsets.US_ASCII))
        }.array()
        val file = writeMp4(testDir, "child_overruns.mp4") {
            +ftyp()
            +moov(mvhdHeader)
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `size equals zero inside moov is rejected`() {
        // size==0 means "extends to EOF" — only valid at top level.
        val badChild = ByteBuffer.allocate(12).apply {
            putInt(0) // size == 0
            put("mvhd".toByteArray(Charsets.US_ASCII))
            putInt(0) // would-be version+flags
        }.array()
        val file = writeMp4(testDir, "size_zero_in_moov.mp4") {
            +ftyp()
            +moov(badChild)
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `unknown FullBox version is rejected`() {
        val mvhdV2 = ByteBuffer.allocate(20).apply {
            putInt(2 shl 24) // version=2, flags=0
            putLong(0L)
            putLong(0L)
        }.array()
        val file = writeMp4(testDir, "mvhd_v2.mp4") {
            +ftyp()
            +moov(box("mvhd", mvhdV2))
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `creation time of zero is rejected`() {
        val file = writeMp4(testDir, "zero_creation.mp4") {
            +ftyp()
            +moov(mvhdV0(creationMp4Seconds = 0L, modificationMp4Seconds = 0L))
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `truncated mvhd payload is rejected`() {
        // mvhd box with payload smaller than required (only version+flags, no times).
        val truncatedMvhdPayload = ByteArray(4) // FullBox header only
        val file = writeMp4(testDir, "truncated_mvhd.mp4") {
            +ftyp()
            +moov(box("mvhd", truncatedMvhdPayload))
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `moov without mvhd is rejected`() {
        val file = writeMp4(testDir, "no_mvhd.mp4") {
            +ftyp()
            +moov(box("udta", ByteArray(8))) // sibling box, but no mvhd
        }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    @Test
    fun `file with no moov returns null`() {
        val file = File(testDir, "no_moov.mp4").apply { writeBytes(ftyp()) }

        subject.extractFromMvhd(file).shouldBeNull()
    }

    // ----- helpers -----

    private fun unixToMp4(unixSeconds: Long): Long =
        unixSeconds + VideoTimestampPreserver.MP4_EPOCH_OFFSET_SECONDS

    /** Build an `ftyp` box: type "ftyp" with a minimal payload. */
    private fun ftyp(): ByteArray = box("ftyp", ByteArray(8) { 0 })

    /** Build a `moov` container box wrapping the given children. */
    private fun moov(vararg children: ByteArray): ByteArray =
        box("moov", concat(*children))

    /** Build a v0 `mvhd` box with the given creation and modification times (MP4 seconds). */
    private fun mvhdV0(creationMp4Seconds: Long, modificationMp4Seconds: Long): ByteArray {
        val payload = ByteBuffer.allocate(12).apply {
            putInt(0) // version=0, flags=0
            putInt((creationMp4Seconds and 0xffffffffL).toInt())
            putInt((modificationMp4Seconds and 0xffffffffL).toInt())
        }.array()
        return box("mvhd", payload)
    }

    /** Build a v1 `mvhd` box with 64-bit creation and modification times. */
    private fun mvhdV1(creationMp4Seconds: Long, modificationMp4Seconds: Long): ByteArray {
        val payload = ByteBuffer.allocate(20).apply {
            putInt(1 shl 24) // version=1, flags=0
            putLong(creationMp4Seconds)
            putLong(modificationMp4Seconds)
        }.array()
        return box("mvhd", payload)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val totalSize = 8 + payload.size
        return ByteBuffer.allocate(totalSize).apply {
            putInt(totalSize)
            put(type.toByteArray(Charsets.US_ASCII))
            put(payload)
        }.array()
    }

    private fun largesizeBox(type: String, payload: ByteArray): ByteArray {
        val totalSize = 16 + payload.size
        return ByteBuffer.allocate(totalSize).apply {
            putInt(1) // size==1 → largesize follows
            put(type.toByteArray(Charsets.US_ASCII))
            putLong(totalSize.toLong())
            put(payload)
        }.array()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val total = arrays.sumOf { it.size }
        val out = ByteArray(total)
        var off = 0
        for (a in arrays) {
            a.copyInto(out, off)
            off += a.size
        }
        return out
    }

    /** Tiny DSL so the test reads top-down like an MP4 file structure. */
    private class FileBuilder {
        val parts = mutableListOf<ByteArray>()
        operator fun ByteArray.unaryPlus() {
            parts += this
        }
    }

    private fun writeMp4(dir: File, name: String, block: FileBuilder.() -> Unit): File {
        val builder = FileBuilder().apply(block)
        val file = File(dir, name)
        file.outputStream().use { out ->
            for (part in builder.parts) out.write(part)
        }
        return file
    }

    companion object {
        private const val IO_TEST_BASEDIR = "build/tmp/unit_tests"
    }
}
