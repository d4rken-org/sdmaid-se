package eu.darken.sdmse.squeezer.core.processor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class HeifExifExtractorTest : BaseTest() {

    private lateinit var testDir: File
    private val subject = HeifExifExtractor()

    @BeforeEach
    fun setup() {
        testDir = File("build/tmp/heif_extractor_test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @AfterEach
    fun teardown() {
        testDir.deleteRecursively()
    }

    @Test
    fun `extracts Exif APP1 block from a spec-compliant libheif HEIC`() {
        val fixture = copyResource("tiny_with_exif.heic")

        val result = subject.extractExifBlock(fixture)
        val extracted = result.shouldBeInstanceOf<HeifExifExtractor.Result.Extracted>()
        val payload = extracted.bytes

        // Must start with "Exif  ".
        String(payload, 0, 4, Charsets.US_ASCII) shouldBe "Exif"
        payload[4].toInt() shouldBe 0
        payload[5].toInt() shouldBe 0

        // After the marker, a TIFF header follows.
        val byteOrder = String(payload, 6, 2, Charsets.US_ASCII)
        (byteOrder == "II" || byteOrder == "MM") shouldBe true
    }

    @Test
    fun `extracted block contains expected EXIF tag bytes`() {
        val fixture = copyResource("tiny_with_exif.heic")
        val result = subject.extractExifBlock(fixture)
        val extracted = result.shouldBeInstanceOf<HeifExifExtractor.Result.Extracted>()
        val payload = extracted.bytes

        // The fixture was built with Make="TestVendor" — the ASCII bytes should be present
        // somewhere in the TIFF blob (in the data area where strings live).
        val asString = String(payload, Charsets.ISO_8859_1)
        (asString.contains("TestVendor")) shouldBe true
        (asString.contains("TestModel")) shouldBe true
    }

    @Test
    fun `garbage bytes produce Unsupported, not silent failure`() {
        // A file that fails ISOBMFF parsing should NOT silently return NoExif — the caller would
        // then compress without metadata. Force the contract: anything that can't be parsed is
        // an Unsupported (and ImageCompressor will abort rather than strip metadata).
        val notHeif = File(testDir, "garbage.bin").apply { writeBytes(ByteArray(64) { 0x42 }) }
        val result = subject.extractExifBlock(notHeif)
        result.shouldBeInstanceOf<HeifExifExtractor.Result.Unsupported>()
    }

    @Test
    fun `missing file produces Unsupported`() {
        val result = subject.extractExifBlock(File(testDir, "does_not_exist.heic"))
        result.shouldBeInstanceOf<HeifExifExtractor.Result.Unsupported>()
    }

    @Test
    fun `valid HEIF without an Exif item returns NoExif`() {
        // A structurally valid HEIF (ftyp + meta{iinf, iloc}) whose only item is the primary
        // image and which carries no Exif item. The extractor must report NoExif (compressing
        // without metadata is the correct outcome) rather than Unsupported (which would abort
        // compression for a file that legitimately has nothing to preserve).
        val fixture = File(testDir, "no_exif.heic").apply { writeBytes(buildHeifWithoutExif()) }

        val result = subject.extractExifBlock(fixture)
        result.shouldBeInstanceOf<HeifExifExtractor.Result.NoExif>()
    }

    private fun copyResource(name: String): File {
        val out = File(testDir, name)
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        return out
    }

    /**
     * Minimal ISOBMFF: `ftyp` + `meta{ iinf(one non-Exif infe), iloc(empty) }`. Exercises the full
     * meta→iinf walk and confirms a container whose only item is the primary image (item_type
     * "hvc1", no "Exif" item) resolves to NoExif. The `iloc` must be present (the parser looks it
     * up before the item search) but its body is never read on this path.
     */
    private fun buildHeifWithoutExif(): ByteArray {
        val ftyp = box("ftyp", "heic".ascii() + beInt(0) + "heic".ascii())

        // infe v2: version+flags, item_id, item_protection_index, item_type("hvc1").
        val infe = box("infe", beInt(0x02000000) + beShort(1) + beShort(0) + "hvc1".ascii())
        // iinf v0: version+flags, entry_count=1, then the single infe entry.
        val iinf = box("iinf", beInt(0) + beShort(1) + infe)
        val iloc = box("iloc", ByteArray(0))
        // meta is a FullBox: 4-byte version+flags, then its child boxes.
        val meta = box("meta", beInt(0) + iinf + iloc)

        return ftyp + meta
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        beInt(8 + payload.size) + type.ascii() + payload

    private fun beInt(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun beShort(v: Int): ByteArray =
        byteArrayOf((v ushr 8).toByte(), v.toByte())

    private fun String.ascii(): ByteArray = toByteArray(Charsets.US_ASCII)
}
