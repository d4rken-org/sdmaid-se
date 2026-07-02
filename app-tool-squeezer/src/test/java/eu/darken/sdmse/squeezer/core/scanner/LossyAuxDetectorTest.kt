package eu.darken.sdmse.squeezer.core.scanner

import eu.darken.sdmse.squeezer.core.CompressibleImage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class LossyAuxDetectorTest : BaseTest() {

    private val detector = LossyAuxDetector()

    private fun tmp(bytes: ByteArray, ext: String): File =
        File.createTempFile("lossyaux_", ".$ext").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

    /** Minimal JPEG: SOI, one APP1 per payload, then SOS. */
    private fun jpeg(vararg app1Payloads: String): ByteArray {
        val out = ArrayList<Byte>()
        out += 0xFF.toByte(); out += 0xD8.toByte() // SOI
        for (p in app1Payloads) {
            val payload = p.toByteArray(Charsets.ISO_8859_1)
            val segLen = payload.size + 2
            out += 0xFF.toByte(); out += 0xE1.toByte() // APP1
            out += (segLen ushr 8 and 0xFF).toByte(); out += (segLen and 0xFF).toByte()
            out += payload.toList()
        }
        out += 0xFF.toByte(); out += 0xDA.toByte() // SOS
        return out.toByteArray()
    }

    /** Minimal ISOBMFF: an `ftyp` box followed by a `meta` FullBox holding [metaContent]. */
    private fun heif(metaContent: String): ByteArray {
        val out = ArrayList<Byte>()
        fun box(type: String, content: ByteArray) {
            val size = 8 + content.size
            out += (size ushr 24 and 0xFF).toByte(); out += (size ushr 16 and 0xFF).toByte()
            out += (size ushr 8 and 0xFF).toByte(); out += (size and 0xFF).toByte()
            out += type.toByteArray(Charsets.ISO_8859_1).toList()
            out += content.toList()
        }
        box("ftyp", "heic".toByteArray(Charsets.ISO_8859_1) + ByteArray(4))
        // meta is a FullBox -> 4 version/flags bytes precede the content we scan.
        box("meta", ByteArray(4) + metaContent.toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun leU16(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v ushr 8 and 0xFF).toByte())
    private fun leU32(v: Long) = byteArrayOf(
        (v and 0xFF).toByte(), (v ushr 8 and 0xFF).toByte(),
        (v ushr 16 and 0xFF).toByte(), (v ushr 24 and 0xFF).toByte(),
    )

    /** Real ISOBMFF (big-endian boxes): `ftyp` + `meta{ iinf[one infe of itemType], iloc }`. */
    private fun heifWithItemType(
        itemType: String,
        infeVersion: Int = 2,
        brokenIinfSize: Boolean = false,
    ): ByteArray {
        require(itemType.length == 4)
        fun beU32(v: Int) = byteArrayOf(
            (v ushr 24 and 0xFF).toByte(), (v ushr 16 and 0xFF).toByte(),
            (v ushr 8 and 0xFF).toByte(), (v and 0xFF).toByte(),
        )
        fun beU16(v: Int) = byteArrayOf((v ushr 8 and 0xFF).toByte(), (v and 0xFF).toByte())
        fun box(type: String, payload: ByteArray) = beU32(8 + payload.size) + ascii(type) + payload
        // infe version+flags, item_id (2 bytes @v2 / 4 bytes @v3), item_protection_index, item_type
        val infePayload = when (infeVersion) {
            3 -> beU32(0x03000000) + beU32(1) + beU16(0) + ascii(itemType)
            else -> beU32(0x02000000) + beU16(1) + beU16(0) + ascii(itemType)
        }
        val infe = box("infe", infePayload)
        var iinf = box("iinf", beU32(0) + beU16(1) + infe) // iinf v0, entry_count=1
        if (brokenIinfSize) iinf = beU32(0) + iinf.copyOfRange(4, iinf.size) // size 0 -> malformed
        val iloc = box("iloc", ByteArray(0))
        val meta = box("meta", beU32(0) + iinf + iloc) // FullBox
        return box("ftyp", ascii("heic") + beU32(0) + ascii("heic")) + meta
    }

    /** Samsung SEF trailer (little-endian) with one data block named [blockName]. */
    private fun sefTrailer(blockName: String): ByteArray {
        val name = ascii(blockName)
        val data = ByteArray(8) { 0x11 }
        val block = byteArrayOf(0, 0) + leU16(0x0100) + leU32(name.size.toLong()) + name + data
        val entry = byteArrayOf(0, 0) + leU16(0x0100) + leU32(block.size.toLong()) + leU32(block.size.toLong())
        val dir = ascii("SEFH") + leU32(101) + leU32(1) + entry
        return block + dir + (leU32(dir.size.toLong()) + ascii("SEFT"))
    }

    /** SEF trailer with a trailing QDIO (Sound&Shot) block, so the file ends in "QDIOBS" not "SEFT". */
    private fun sefTrailerWithQdio(blockName: String): ByteArray {
        // QDIO block: ['QDIO'][101][1][audioStart][audioEnd] payload(20) + [len=20]['QDIO'], then 'BS'.
        val qdioPayload = ascii("QDIO") + leU32(101) + leU32(1) + leU32(0) + leU32(0)
        val qdio = qdioPayload + leU32(qdioPayload.size.toLong()) + ascii("QDIO")
        return sefTrailer(blockName) + qdio + ascii("BS")
    }

    /** Minimal JPEG (SOI+EOI, or a broken head when [soi]=false) followed by an SEF trailer. */
    private fun jpegWithSef(blockName: String, soi: Boolean = true): ByteArray {
        val head = if (soi) {
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        } else {
            byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0xD9.toByte())
        }
        return head + sefTrailer(blockName)
    }

    private fun jpegHead() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    private val jpeg = CompressibleImage.MIME_TYPE_JPEG
    private val webp = CompressibleImage.MIME_TYPE_WEBP
    private val heic = CompressibleImage.MIME_TYPE_HEIC

    @Test
    fun `jpeg with HDRGM namespace is a gain map`() {
        detector.hasLossyAux(tmp(jpeg("""<x xmlns:hdrgm="..."><hdrgm:Version>1.0</hdrgm:Version>"""), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `jpeg with Adobe gain-map URI is a gain map`() {
        detector.hasLossyAux(tmp(jpeg("ns=http://ns.adobe.com/hdr-gain-map/1.0/"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `jpeg with Apple HDRGainMap is a gain map`() {
        detector.hasLossyAux(tmp(jpeg("apple_desktop:HDRGainMap=true"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `jpeg with GDepth is depth data`() {
        detector.hasLossyAux(tmp(jpeg("""<rdf xmlns:GDepth="http://ns.google.com/..."/>"""), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `plain jpeg is not flagged`() {
        detector.hasLossyAux(tmp(jpeg("an ordinary XMP packet with no aux markers"), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `bare GainMap word in a caption is not a false positive`() {
        detector.hasLossyAux(tmp(jpeg("dc:description=My GainMap mountain holiday"), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `bare HDRGainMap word in a caption is not a false positive`() {
        detector.hasLossyAux(tmp(jpeg("dc:description=shot in HDRGainMap mode"), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `jpeg with Google depthmap namespace URI is depth data`() {
        detector.hasLossyAux(tmp(jpeg("ns=http://ns.google.com/photos/1.0/depthmap/"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `signature in a later APP1 after a large one is still found`() {
        val big = "X".repeat(60_000)
        detector.hasLossyAux(tmp(jpeg(big, "tail hdrgm:Version end"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `heif with Apple gain-map URN is a gain map`() {
        detector.hasLossyAux(tmp(heif("aux urn:com:apple:photo:2020:aux:hdrgainmap here"), "heic"), heic) shouldBe true
    }

    @Test
    fun `heif with Apple disparity URN is depth data`() {
        detector.hasLossyAux(tmp(heif("aux urn:com:apple:photo:2020:aux:disparity here"), "heic"), heic) shouldBe true
    }

    @Test
    fun `plain heif is not flagged`() {
        detector.hasLossyAux(tmp(heif("iinf infe Exif hvc1 nothing special"), "heic"), heic) shouldBe false
    }

    @Test
    fun `webp is never scanned`() {
        detector.hasLossyAux(tmp(byteArrayOf(0x52, 0x49, 0x46, 0x46), "webp"), webp) shouldBe false
    }

    @Test
    fun `missing file fails open to false`() {
        detector.hasLossyAux(File("/does/not/exist/x.jpg"), jpeg) shouldBe false
    }

    @Test
    fun `garbage bytes fail open to false`() {
        detector.hasLossyAux(tmp(ByteArray(256) { 0x7F }, "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `truncated heif fails open to false`() {
        detector.hasLossyAux(tmp(byteArrayOf(0, 0, 0), "heic"), heic) shouldBe false
    }

    @Test
    fun `heif with a tmap gain-map item is a gain map`() {
        detector.hasLossyAux(tmp(heifWithItemType("tmap"), "heic"), heic) shouldBe true
    }

    @Test
    fun `heif whose only item is the primary image is not flagged`() {
        detector.hasLossyAux(tmp(heifWithItemType("hvc1"), "heic"), heic) shouldBe false
    }

    @Test
    fun `heif with a v3 infe tmap item is a gain map`() {
        detector.hasLossyAux(tmp(heifWithItemType("tmap", infeVersion = 3), "heic"), heic) shouldBe true
    }

    @Test
    fun `bare tmap text without a structural infe is not a false positive`() {
        // 'tmap' appearing only as free text in the meta box must not trip the structural iinf walk.
        detector.hasLossyAux(tmp(heif("caption: shot with tmap tone mapping, no real item"), "heic"), heic) shouldBe false
    }

    @Test
    fun `malformed HEIF with a size-zero iinf box fails open to false`() {
        detector.hasLossyAux(tmp(heifWithItemType("tmap", brokenIinfSize = true), "heic"), heic) shouldBe false
    }

    @Test
    fun `jpeg with a Samsung SEF DepthMap trailer is depth data`() {
        detector.hasLossyAux(tmp(jpegWithSef("DualShot_DepthMap_1"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `jpeg with a non-depth SEF trailer is not flagged`() {
        // A valid SEF trailer without a DepthMap block (e.g. Sound&Shot metadata) must not be skipped.
        detector.hasLossyAux(tmp(jpegWithSef("DualShot_Meta_Info"), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `jpeg whose depth SEF is followed by a QDIO block (QDIOBS tail) is still depth data`() {
        // The SEFT directory isn't last; the backward walk must skip the trailing QDIO block.
        detector.hasLossyAux(tmp(jpegHead() + sefTrailerWithQdio("DualShot_DepthMap_1"), "jpg"), jpeg) shouldBe true
    }

    @Test
    fun `jpeg with a non-depth SEF behind a QDIO block is not flagged`() {
        detector.hasLossyAux(tmp(jpegHead() + sefTrailerWithQdio("DualShot_Meta_Info"), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `SEF depth tail without a JPEG SOI is not a false positive`() {
        detector.hasLossyAux(tmp(jpegWithSef("DualShot_DepthMap_1", soi = false), "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `short SEF directory fails open to false`() {
        // Tail claims a 4-byte directory (< the 12-byte SEFH minimum).
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()) +
            ByteArray(8) + leU32(4) + ascii("SEFT")
        detector.hasLossyAux(tmp(bytes, "jpg"), jpeg) shouldBe false
    }

    @Test
    fun `malformed APP segment does not prevent SEF depth detection`() {
        // APP1 declares a bogus over-long length (the XMP scan throws/bails); the isolated SEF
        // trailer check must still run and find the depth block.
        val brokenApp1 = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE1.toByte(), // APP1 marker
            0xFF.toByte(), 0xFF.toByte(), // length = 65535 (payload far exceeds the file)
        )
        detector.hasLossyAux(tmp(brokenApp1 + sefTrailer("DualShot_DepthMap_1"), "jpg"), jpeg) shouldBe true
    }
}
