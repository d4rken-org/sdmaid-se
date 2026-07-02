package eu.darken.sdmse.squeezer.core.processor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class HeifTransformInspectorTest : BaseTest() {

    private lateinit var testDir: File
    private val subject = HeifTransformInspector()
    private var counter = 0

    @BeforeEach
    fun setup() {
        testDir = File("build/tmp/heif_transform_test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @AfterEach
    fun teardown() {
        testDir.deleteRecursively()
    }

    private fun u32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    private fun box(type: String, payload: ByteArray): ByteArray =
        u32(8 + payload.size) + type.toByteArray(Charsets.US_ASCII) + payload

    private fun irot(angle: Int) = box("irot", byteArrayOf(angle.toByte()))
    private fun imir(axis: Int = 0) = box("imir", byteArrayOf(axis.toByte()))
    private fun ispe(w: Int, h: Int) = box("ispe", u32(0) + u32(w) + u32(h))

    private fun ipmaBox(
        associations: List<Pair<Int, List<Int>>>,
        wideIndices: Boolean = false,
        version: Int = 0,
        essentialBits: Boolean = false,
    ): ByteArray {
        val payload = u32((version shl 24) or (if (wideIndices) 1 else 0)) +
            u32(associations.size) +
            associations.fold(ByteArray(0)) { acc, (itemId, indices) ->
                acc + (if (version < 1) u16(itemId) else u32(itemId)) +
                    byteArrayOf(indices.size.toByte()) +
                    indices.fold(ByteArray(0)) { a, i ->
                        val value = if (essentialBits) i or (if (wideIndices) 0x8000 else 0x80) else i
                        a + if (wideIndices) u16(value) else byteArrayOf(value.toByte())
                    }
            }
        return box("ipma", payload)
    }

    /**
     * Builds a minimal HEIC: a single `meta` box holding `pitm` + `iprp(ipco + ipma)`.
     * [associations] maps item ids to 1-based ipco property indices.
     */
    private fun buildHeic(
        primaryId: Int = 1,
        props: List<ByteArray> = emptyList(),
        associations: List<Pair<Int, List<Int>>> = emptyList(),
        wideIndices: Boolean = false,
        includePitm: Boolean = true,
        includeIpma: Boolean = true,
        ipmaBoxes: List<ByteArray>? = null,
        extraIprpBytes: ByteArray = ByteArray(0),
        extraMetaBytes: ByteArray = ByteArray(0),
    ): File {
        val pitm = box("pitm", u32(0) + u16(primaryId))
        val ipco = box("ipco", props.fold(ByteArray(0)) { acc, b -> acc + b })
        val ipmas = ipmaBoxes
            ?: if (includeIpma) listOf(ipmaBox(associations, wideIndices)) else emptyList()
        val iprp = box("iprp", ipco + ipmas.fold(ByteArray(0)) { acc, b -> acc + b } + extraIprpBytes)
        val meta = box("meta", u32(0) + (if (includePitm) pitm else ByteArray(0)) + iprp + extraMetaBytes)
        return File(testDir, "synthetic-${counter++}.heic").apply { writeBytes(meta) }
    }

    @Test
    fun `no transform properties yields NoTransform`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024)),
            associations = listOf(1 to listOf(1)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.NoTransform
    }

    @Test
    fun `irot on the primary item is reported with the ispe dimensions`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(1 to listOf(1, 2)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.Rotated(
            angleCcw = 1,
            storedWidth = 4032,
            storedHeight = 3024,
        )
    }

    @Test
    fun `irot with angle 0 counts as no transform`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(0)),
            associations = listOf(1 to listOf(1, 2)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.NoTransform
    }

    @Test
    fun `imir on the primary item is reported as Mirrored even alongside irot`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1), imir()),
            associations = listOf(1 to listOf(1, 2, 3)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.Mirrored
    }

    @Test
    fun `duplicate irot on the primary item is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1), irot(3)),
            associations = listOf(1 to listOf(1, 2, 3)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `irot without ispe dimensions is Unsupported`() {
        val file = buildHeic(
            props = listOf(irot(1)),
            associations = listOf(1 to listOf(1)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `ipma referencing a property beyond ipco is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024)),
            associations = listOf(1 to listOf(1, 5)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `property index 0 means no property and is skipped`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(1 to listOf(0, 1, 2)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Rotated>()
    }

    @Test
    fun `transforms on non-primary items are ignored`() {
        val file = buildHeic(
            primaryId = 1,
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(
                1 to listOf(1),
                2 to listOf(2), // thumbnail carries the irot
            ),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.NoTransform
    }

    @Test
    fun `wide 15-bit property indices parse correctly`() {
        val file = buildHeic(
            props = listOf(ispe(1280, 720), irot(3)),
            associations = listOf(1 to listOf(1, 2)),
            wideIndices = true,
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.Rotated(
            angleCcw = 3,
            storedWidth = 1280,
            storedHeight = 720,
        )
    }

    @Test
    fun `no ipma entry for the primary item yields NoTransform`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(2 to listOf(1, 2)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.NoTransform
    }

    @Test
    fun `duplicate ipma entries for the primary item are Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(1 to listOf(1), 1 to listOf(2)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `missing pitm is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024)),
            associations = listOf(1 to listOf(1)),
            includePitm = false,
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `associations in a second ipma box are found`() {
        // Real files may split associations across several ipma boxes; missing the second one
        // would fail open as NoTransform and silently drop the rotation.
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            ipmaBoxes = listOf(
                ipmaBox(listOf(2 to listOf(1))),
                ipmaBox(listOf(1 to listOf(1, 2))),
            ),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Rotated>()
    }

    @Test
    fun `primary item associated in multiple ipma boxes is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            ipmaBoxes = listOf(
                ipmaBox(listOf(1 to listOf(1))),
                ipmaBox(listOf(1 to listOf(2))),
            ),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `ipma with version 1 item ids parses correctly`() {
        val file = buildHeic(
            props = listOf(ispe(1280, 720), irot(1)),
            ipmaBoxes = listOf(ipmaBox(listOf(1 to listOf(1, 2)), version = 1)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Rotated>()
    }

    @Test
    fun `ipma with an unknown version is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(1280, 720), irot(1)),
            ipmaBoxes = listOf(ipmaBox(listOf(1 to listOf(1, 2)), version = 2)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `essential flag bits on property indices are masked off`() {
        val file = buildHeic(
            props = listOf(ispe(1280, 720), irot(1)),
            ipmaBoxes = listOf(ipmaBox(listOf(1 to listOf(1, 2)), essentialBits = true)),
        )
        subject.inspect(file) shouldBe HeifTransformInspector.Result.Rotated(
            angleCcw = 1,
            storedWidth = 1280,
            storedHeight = 720,
        )

        val wide = buildHeic(
            props = listOf(ispe(1280, 720), irot(1)),
            ipmaBoxes = listOf(ipmaBox(listOf(1 to listOf(1, 2)), wideIndices = true, essentialBits = true)),
        )
        subject.inspect(wide).shouldBeInstanceOf<HeifTransformInspector.Result.Rotated>()
    }

    @Test
    fun `truncated ipma is Unsupported`() {
        // Claims 5 associations for the entry but the box ends first: must not read into
        // sibling boxes or fail open.
        val payload = u32(0) + u32(1) + u16(1) + byteArrayOf(5) + byteArrayOf(1)
        val file = buildHeic(
            props = listOf(ispe(1280, 720), irot(1)),
            ipmaBoxes = listOf(box("ipma", payload)),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `malformed sibling box inside iprp is Unsupported not NoTransform`() {
        // A box claiming a 3-byte size is structurally invalid; treating it as "no ipma found"
        // would fail open and drop a real rotation elsewhere in the container.
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(1 to listOf(1, 2)),
            extraIprpBytes = u32(3),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `malformed sibling box inside meta is Unsupported`() {
        val file = buildHeic(
            props = listOf(ispe(4032, 3024), irot(1)),
            associations = listOf(1 to listOf(1, 2)),
            extraMetaBytes = u32(3),
        )
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `garbage input is Unsupported`() {
        val file = File(testDir, "garbage.heic").apply { writeBytes("not a heic at all".toByteArray()) }
        subject.inspect(file).shouldBeInstanceOf<HeifTransformInspector.Result.Unsupported>()
    }

    @Test
    fun `real libheif fixture without rotation yields NoTransform`() {
        subject.inspect(copyResource("tiny_with_exif.heic")) shouldBe HeifTransformInspector.Result.NoTransform
    }

    @Test
    fun `real HeifWriter fixture with setRotation 90 is detected`() {
        // Produced on a Pixel 8 via HeifWriter.setRotation(90): grid-based primary item with
        // irot angle 3 (= 90° clockwise) and ispe 1280x720. The round trip back through
        // decideHeicRotation must reproduce the 90° clockwise value.
        val result = subject.inspect(copyResource("heifwriter_rot90.heic"))
        val rotated = result.shouldBeInstanceOf<HeifTransformInspector.Result.Rotated>()
        rotated.angleCcw shouldBe 3
        rotated.storedWidth shouldBe 1280
        rotated.storedHeight shouldBe 720

        ImageCompressor.decideHeicRotation(rotated, decodedWidth = 1280, decodedHeight = 720)
            .shouldBeInstanceOf<ImageCompressor.RotationDecision.Propagate>()
            .degreesCw shouldBe 90
    }

    private fun copyResource(name: String): File {
        val target = File(testDir, name)
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }
}
