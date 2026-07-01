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
 * - JPEG: (a) walk the APP markers up to the SOS and match namespaced signatures inside the
 *   APP1/APP2 (XMP/Exif/MPF) segments — *not* bare words, so an XMP caption containing the text
 *   "GainMap" cannot trigger a false positive; and (b) parse the Samsung SEF trailer appended after
 *   the JPEG EOI for a `DualShot_DepthMap` block (portrait/Live-Focus depth).
 * - HEIF: locate the `meta` box and match Apple auxiliary-image URNs, and structurally walk `iinf`
 *   for an ISO 21496-1 `tmap` (tone-map) gain-map item.
 *
 * **Fail-open is required.** Any IO/parse error, a missing/non-readable file, or an unrecognised
 * format returns `false` (and any single sub-check that throws is isolated so the others still run).
 * The opt-in setting that consumes this defaults to "skip these photos", so a fail-closed detector
 * would skip every un-parseable JPEG/HEIC and regress ordinary compression for everyone.
 *
 * Known limitation: for HEIC, a positive result is a *scan-time exclusion*; the "compress them
 * anyway" opt-in still can't force compression of a multi-image HEIC (the hard multi-image guard in
 * `MediaScanner` can't tell a gain-map sub-image from Live-Photo siblings).
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
            // SOI gate: only trust a JPEG. Also stops a non-JPEG blob that merely ends in a "SEFT"
            // tail from tripping the SEF check below.
            if (raf.length() < 4) return false
            raf.seek(0)
            if (raf.read() != 0xFF || raf.read() != 0xD8) return false

            // Each sub-check is isolated so a throw in one still lets the other run (fail-open).
            if (runAux(file) { hasJpegXmpAux(raf) }) return true
            if (runAux(file) { hasSefDepthTrailer(raf) }) return true
        }
        return false
    }

    /** Runs a sub-check, swallowing any exception as `false` so a sibling check still runs. */
    private inline fun runAux(file: File, block: () -> Boolean): Boolean = try {
        block()
    } catch (e: Exception) {
        log(TAG, WARN) { "aux sub-check failed for ${file.path} (fail-open): ${e.message}" }
        false
    }

    /** Scans APP1/APP2 segments (up to SOS) for namespaced gain-map/depth XMP signatures. */
    private fun hasJpegXmpAux(raf: RandomAccessFile): Boolean {
        val len = raf.length()
        raf.seek(2) // past the SOI already validated by the caller
        while (raf.filePointer < len) {
            val b = raf.read()
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
                            log(TAG, VERBOSE) { "JPEG XMP lossy-aux signature found" }
                            return true
                        }
                    }
                    if (payloadStart + payloadLen > len) return false
                    raf.seek(payloadStart + payloadLen)
                }
            }
        }
        return false
    }

    /**
     * Detects a Samsung SEF trailer (appended after the JPEG EOI) carrying a depth block. Parses the
     * trailer structure per ExifTool's `Samsung.pm` `ProcessSamsung` (little-endian). Trailer blocks
     * are framed `[payload][len:u32]["TYPE"]` and read backward from EOF: the file ends either with
     * the `"SEFT"` directory block, or — when a `"QDIO"` (Sound&Shot audio) block trails it — with
     * `"QDIOBS"` (a 2-byte `"BS"` suffix). We walk backward, skipping non-`SEFT` blocks, until the
     * `SEFT` directory is found, then check it for a depth block (see [sefDirectoryHasDepth]).
     *
     * Note: the QDIO-trailing case can't be end-to-end verified against a real device file (none is
     * publicly available), but detection is **fail-open** — a mis-parse here only yields a false
     * negative (missed depth), never a false positive, since a match still requires a positively
     * named depth block inside a valid `SEFH` directory.
     */
    private fun hasSefDepthTrailer(raf: RandomAccessFile): Boolean {
        val fileLen = raf.length()
        if (fileLen < MIN_SEF_LEN) return false

        val tail = ByteArray(6)
        raf.seek(fileLen - 6)
        raf.readFully(tail)
        // "…SEFT" -> directory block ends at EOF; "QDIOBS" -> skip the trailing 2-byte "BS".
        var blockEnd = when {
            tail.asciiAt(2, SEF_TAIL_MAGIC) -> fileLen
            tail.asciiAt(0, SEF_QDIO_TAIL_MAGIC) -> fileLen - 2
            else -> return false
        }

        var walked = 0
        while (walked++ < MAX_SEF_BLOCKS && blockEnd >= 8) {
            val framing = ByteArray(8)
            raf.seek(blockEnd - 8)
            raf.readFully(framing)
            val len = framing.u32(0)
            if (len < 4L || len > MAX_SEF_DIR || len + 8L > blockEnd) return false
            val blockStart = blockEnd - 8 - len
            if (blockStart < 0L) return false
            if (framing.asciiAt(4, SEF_TAIL_MAGIC)) {
                return sefDirectoryHasDepth(raf, blockStart, len)
            }
            // Not the directory (e.g. a QDIO block) — skip it and keep walking backward.
            blockEnd = blockStart
        }
        return false
    }

    /** Reads the `SEFH` directory at [dirStart] and returns true if any data block is a depth map. */
    private fun sefDirectoryHasDepth(raf: RandomAccessFile, dirStart: Long, dirLen: Long): Boolean {
        if (dirLen < 12L || dirLen > MAX_SEF_DIR) return false
        val dir = ByteArray(dirLen.toInt())
        raf.seek(dirStart)
        raf.readFully(dir)
        if (!dir.asciiAt(0, SEF_DIR_MAGIC)) return false

        val count = dir.u32(8)
        if (count > MAX_SEF_ENTRIES || 12L + 12L * count > dirLen) return false

        for (i in 0 until count) {
            val entry = (12L + 12L * i).toInt()
            val noff = dir.u32(entry + 4)
            val size = dir.u32(entry + 8)
            if (noff > dirStart || size < 8L || size > noff) continue
            val blockStart = dirStart - noff
            if (blockStart < 0L || blockStart + size > dirStart) continue

            val toRead = minOf(size, 8L + MAX_SEF_NAME).toInt()
            val header = ByteArray(toRead)
            raf.seek(blockStart)
            raf.readFully(header)
            if (header.size < 8) continue
            val nameLen = header.u32(4)
            if (nameLen <= 0L || nameLen > minOf(size - 8L, MAX_SEF_NAME)) continue
            val name = String(header, 8, nameLen.toInt(), Charsets.ISO_8859_1)
            if (name.contains(SEF_DEPTH_KEY)) {
                log(TAG, VERBOSE) { "Samsung SEF depth block found: $name" }
                return true
            }
        }
        return false
    }

    private fun hasHeifLossyAux(file: File): Boolean {
        RandomAccessFile(file, "r").use { raf ->
            val meta = findTopLevelBox(raf, BOX_META) ?: return false

            // (a) Apple aux URNs (older iPhone HDR/depth) — substring within the meta box.
            val length = (meta.second - meta.first).coerceAtMost(MAX_META_SCAN)
            if (length > 0) {
                raf.seek(meta.first)
                val buf = ByteArray(length.toInt())
                raf.readFully(buf)
                val text = String(buf, Charsets.ISO_8859_1)
                if (HEIF_AUX_SIGNATURES.any { text.contains(it) }) {
                    log(TAG, VERBOSE) { "HEIF Apple-URN lossy-aux signature found in ${file.name}" }
                    return true
                }
            }

            // (b) ISO 21496-1 gain map: a `tmap` (tone-map) derived item declared in `iinf`.
            if (heifHasTmapItem(raf, meta)) {
                log(TAG, VERBOSE) { "HEIF tmap gain-map item found in ${file.name}" }
                return true
            }
        }
        return false
    }

    /**
     * True if the HEIF `meta` box declares an `infe` with item_type `tmap`. Structure-aware (not a
     * bare-word scan): walks `meta` (FullBox) → `iinf` (FullBox) → each `infe`, reading the item_type
     * at the version-specific offset (`infe` v2/v3, same layout as `HeifExifExtractor.readInfeItem`).
     * Every read is length-guarded; anything unexpected returns `false` (fail-open).
     */
    private fun heifHasTmapItem(raf: RandomAccessFile, meta: Pair<Long, Long>): Boolean {
        val metaContentStart = meta.first + 4 // meta is a FullBox (skip version+flags)
        val metaEnd = meta.second
        val iinf = findChildBox(raf, metaContentStart, metaEnd, BOX_IINF) ?: return false
        val (iinfStart, iinfEnd) = iinf
        if (iinfStart + 4 > iinfEnd) return false

        raf.seek(iinfStart)
        val version = (raf.readInt() ushr 24) and 0xff
        val entryCount: Long
        var pos: Long
        if (version == 0) {
            if (iinfStart + 6 > iinfEnd) return false
            entryCount = (raf.readShort().toInt() and 0xffff).toLong()
            pos = iinfStart + 6
        } else {
            if (iinfStart + 8 > iinfEnd) return false
            entryCount = raf.readInt().toLong() and 0xffffffffL
            pos = iinfStart + 8
        }

        var seen = 0L
        while (seen < entryCount && pos + 8 <= iinfEnd) {
            val box = readBoxAt(raf, pos, iinfEnd) ?: break
            if (box.type == BOX_INFE && infeItemTypeIsTmap(raf, box.payloadStart, box.boxEnd)) return true
            pos = box.boxEnd
            seen++
        }
        return false
    }

    private fun infeItemTypeIsTmap(raf: RandomAccessFile, payloadStart: Long, boxEnd: Long): Boolean {
        if (payloadStart + 4 > boxEnd) return false
        raf.seek(payloadStart)
        val version = (raf.readInt() ushr 24) and 0xff
        val typeOffset = when (version) {
            2 -> payloadStart + 8  // version+flags(4) + item_id(2) + protection_index(2)
            3 -> payloadStart + 10 // version+flags(4) + item_id(4) + protection_index(2)
            else -> return false
        }
        if (typeOffset + 4 > boxEnd) return false
        raf.seek(typeOffset)
        return raf.readInt() == TYPE_TMAP
    }

    private data class BoxAt(val type: Int, val payloadStart: Long, val boxEnd: Long)

    /** Reads the ISOBMFF box header at [pos], bounded by [parentEnd]. Null on any malformed size. */
    private fun readBoxAt(raf: RandomAccessFile, pos: Long, parentEnd: Long): BoxAt? {
        if (pos + 8 > parentEnd) return null
        raf.seek(pos)
        val sizeRaw = raf.readInt().toLong() and 0xffffffffL
        val type = raf.readInt()
        val payloadStart: Long
        val boxEnd: Long
        when {
            sizeRaw == 1L -> {
                if (pos + 16 > parentEnd) return null
                val largesize = raf.readLong()
                if (largesize < 16L) return null
                payloadStart = pos + 16
                boxEnd = pos + largesize
            }
            // size 0 ("extends to EOF") is only legal for a top-level box; readBoxAt is only ever
            // used for child boxes (iinf/infe), so treat it — and any sub-header size — as malformed.
            sizeRaw < 8L -> return null
            else -> {
                payloadStart = pos + 8
                boxEnd = pos + sizeRaw
            }
        }
        if (boxEnd > parentEnd || boxEnd <= pos) return null
        return BoxAt(type, payloadStart, boxEnd)
    }

    private fun findChildBox(raf: RandomAccessFile, start: Long, end: Long, type: Int): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            val box = readBoxAt(raf, pos, end) ?: return null
            if (box.type == type) return box.payloadStart to box.boxEnd
            pos = box.boxEnd
        }
        return null
    }

    /** Little-endian unsigned 32-bit read from a byte array. */
    private fun ByteArray.u32(offset: Int): Long =
        (this[offset].toLong() and 0xff) or
            ((this[offset + 1].toLong() and 0xff) shl 8) or
            ((this[offset + 2].toLong() and 0xff) shl 16) or
            ((this[offset + 3].toLong() and 0xff) shl 24)

    private fun ByteArray.asciiAt(offset: Int, ascii: String): Boolean {
        if (offset + ascii.length > size) return false
        for (i in ascii.indices) if (this[offset + i] != ascii[i].code.toByte()) return false
        return true
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

        // Samsung SEF trailer parsing bounds (guards against malformed trailers).
        private const val MIN_SEF_LEN = 20L
        private const val MAX_SEF_DIR = 1L * 1024 * 1024   // directory is tiny; cap allocation
        private const val MAX_SEF_ENTRIES = 4096L
        private const val MAX_SEF_NAME = 256L
        private const val MAX_SEF_BLOCKS = 8 // a trailer holds only a handful of framed blocks
        private const val SEF_TAIL_MAGIC = "SEFT"       // directory block type / SEFT-terminated tail
        private const val SEF_QDIO_TAIL_MAGIC = "QDIOBS" // a QDIO audio block trails the directory
        private const val SEF_DIR_MAGIC = "SEFH"
        // Broad on purpose: matches every depth variant (DualShot_DepthMap_N, the misspelled
        // SingeShot_DepthMap_N, …). A false negative here means lost depth, so we bias toward matching.
        private const val SEF_DEPTH_KEY = "DepthMap"

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
        private val BOX_IINF = fourcc("iinf")
        private val BOX_INFE = fourcc("infe")
        private val TYPE_TMAP = fourcc("tmap")
    }
}
