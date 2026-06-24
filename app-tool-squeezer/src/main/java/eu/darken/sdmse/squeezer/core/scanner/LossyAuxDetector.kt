package eu.darken.sdmse.squeezer.core.scanner

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.squeezer.core.CompressibleImage
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Detects whether an image carries auxiliary data the compression pipeline would silently destroy:
 * an **HDR gain map** or a **depth/portrait map**. The Squeezer decodes to a single primary
 * [android.graphics.Bitmap] and re-encodes only that, so a gain map / depth map (a *separate*
 * auxiliary image in the container) cannot survive — an HDR photo becomes plain SDR, a portrait
 * photo loses its depth.
 *
 * Detection is **decode-free** and **structure-aware**:
 * - JPEG: walk the APP markers up to the SOS and match namespaced signatures inside the
 *   APP1/APP2 (XMP/Exif/MPF) segments — *not* bare words, so an XMP caption containing the text
 *   "GainMap" cannot trigger a false positive.
 * - HEIF: locate the `meta` box and scan only within it for Apple auxiliary-image URNs.
 *
 * **Fail-open is required.** Any IO/parse error, a missing/non-readable file, or an unrecognised
 * format returns `false`. The opt-in setting that consumes this defaults to "skip these photos",
 * so a fail-closed detector would skip every un-parseable JPEG/HEIC and regress ordinary
 * compression for everyone.
 *
 * Known v1 limitation: HEIF detection keys off Apple's aux URNs (the realistic HEIF HDR/depth
 * source — iPhones). ISO 21496-1 `tmap` HEIF gain maps are not yet covered. Android phones emit
 * Ultra HDR as JPEG, which the JPEG path handles.
 */
@Reusable
class LossyAuxDetector @Inject constructor() {

    /** Returns true only when a gain-map/depth signature is positively found. Never throws. */
    fun hasLossyAux(file: File, mimeType: String): Boolean = try {
        when {
            mimeType == CompressibleImage.MIME_TYPE_JPEG -> hasJpegLossyAux(file)
            mimeType in CompressibleImage.HEIC_MIME_TYPES -> hasHeifLossyAux(file)
            else -> false // WebP and others can't carry a gain/depth map we would destroy.
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "hasLossyAux failed for ${file.path}; treating as no-aux (fail-open): ${e.message}" }
        false
    }

    private fun hasJpegLossyAux(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            if (len < 4) return false
            if (raf.read() != 0xFF || raf.read() != 0xD8) return false // no SOI -> not a JPEG

            while (raf.filePointer < len) {
                var b = raf.read()
                if (b < 0) return false
                if (b != 0xFF) continue // resync to the next marker
                var marker = raf.read()
                while (marker == 0xFF) marker = raf.read() // skip fill bytes
                when {
                    marker < 0 -> return false
                    marker == 0xD9 -> return false // EOI
                    marker == 0xDA -> return false // SOS: compressed data starts, headers are done
                    marker == 0x01 || marker in 0xD0..0xD7 -> continue // standalone markers, no length
                    else -> {
                        val hi = raf.read()
                        val lo = raf.read()
                        if (hi < 0 || lo < 0) return false
                        val payloadLen = ((hi shl 8) or lo) - 2
                        if (payloadLen <= 0) continue
                        val payloadStart = raf.filePointer
                        // APP1 = Exif/XMP (gain-map metadata), APP2 = MPF/ICC.
                        if (marker == 0xE1 || marker == 0xE2) {
                            val toRead = payloadLen.coerceAtMost(MAX_SEGMENT_SCAN)
                            val buf = ByteArray(toRead)
                            raf.readFully(buf)
                            val text = String(buf, Charsets.ISO_8859_1)
                            if (JPEG_AUX_SIGNATURES.any { text.contains(it) }) {
                                log(TAG, VERBOSE) { "JPEG lossy-aux signature found in ${file.name}" }
                                return true
                            }
                        }
                        if (payloadStart + payloadLen > len) return false
                        raf.seek(payloadStart + payloadLen)
                    }
                }
            }
        }
        return false
    }

    private fun hasHeifLossyAux(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val meta = findTopLevelBox(raf, BOX_META) ?: return false
            val length = (meta.second - meta.first).coerceAtMost(MAX_META_SCAN)
            if (length <= 0) return false
            raf.seek(meta.first)
            val buf = ByteArray(length.toInt())
            raf.readFully(buf)
            val text = String(buf, Charsets.ISO_8859_1)
            if (HEIF_AUX_SIGNATURES.any { text.contains(it) }) {
                log(TAG, VERBOSE) { "HEIF lossy-aux signature found in ${file.name}" }
                return true
            }
        }
        return false
    }

    /**
     * Minimal top-level ISOBMFF box finder — returns (payloadStart, boxEnd) of the first box of
     * [type], or null. Self-contained on purpose: the detector only needs to locate `meta`, not the
     * full `iloc`/extent machinery in [eu.darken.sdmse.squeezer.core.processor.HeifExifExtractor],
     * which stays untouched.
     */
    private fun findTopLevelBox(raf: RandomAccessFile, type: Int): Pair<Long, Long>? {
        val fileLen = raf.length()
        var pos = 0L
        while (pos + 8 <= fileLen) {
            raf.seek(pos)
            val sizeRaw = raf.readInt().toLong() and 0xffffffffL
            val boxType = raf.readInt()
            val payloadStart: Long
            val boxEnd: Long
            when {
                sizeRaw == 1L -> {
                    if (pos + 16 > fileLen) return null
                    val largesize = raf.readLong()
                    if (largesize < 16L) return null
                    payloadStart = pos + 16
                    boxEnd = pos + largesize
                }
                sizeRaw == 0L -> {
                    payloadStart = pos + 8
                    boxEnd = fileLen
                }
                sizeRaw < 8L -> return null
                else -> {
                    payloadStart = pos + 8
                    boxEnd = pos + sizeRaw
                }
            }
            if (boxEnd > fileLen || boxEnd <= pos) return null
            if (boxType == type) return payloadStart to boxEnd
            pos = boxEnd
        }
        return null
    }

    companion object {
        private val TAG = logTag("Squeezer", "Scanner", "LossyAux")

        // APP segments are length-bounded at 65533 bytes; the cap is a belt-and-suspenders guard.
        private const val MAX_SEGMENT_SCAN = 65_536
        private const val MAX_META_SCAN = 8L * 1024 * 1024

        /**
         * Namespaced signatures only (never bare words). The HDRGM namespace is present in every
         * ISO 21496-1 / Ultra HDR gain-map JPEG; the others cover Google's GContainer, Apple, and
         * Google depth maps.
         */
        private val JPEG_AUX_SIGNATURES = listOf(
            "hdr-gain-map",                            // http://ns.adobe.com/hdr-gain-map/1.0/ (ISO 21496-1 / Ultra HDR)
            "hdrgm:",                                  // hdrgm XMP namespace prefix
            "apple_desktop:HDRGainMap",                // Apple gain map (qualified key, not a bare token)
            "http://ns.google.com/photos/1.0/depthmap/", // Google depth-map namespace URI (prefix-agnostic)
            "xmlns:GDepth",                            // Google depth-map namespace declaration
            "GDepth:",                                 // Google depth-map property prefix
        )

        private val HEIF_AUX_SIGNATURES = listOf(
            "urn:com:apple:photo:2020:aux:hdrgainmap",
            "urn:com:apple:photo:2020:aux:disparity",
            "urn:com:apple:photo:2020:aux:depth",
        )

        private fun fourcc(s: String): Int {
            require(s.length == 4)
            return ((s[0].code and 0xff) shl 24) or
                ((s[1].code and 0xff) shl 16) or
                ((s[2].code and 0xff) shl 8) or
                (s[3].code and 0xff)
        }

        private val BOX_META = fourcc("meta")
    }
}
