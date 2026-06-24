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
}
