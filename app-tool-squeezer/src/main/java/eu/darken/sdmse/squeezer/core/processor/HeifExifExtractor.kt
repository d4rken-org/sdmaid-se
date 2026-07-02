package eu.darken.sdmse.squeezer.core.processor

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Extracts the JPEG-APP1-form EXIF byte block from a HEIF/HEIC file by parsing the ISOBMFF box
 * structure directly (`ftyp` → `meta` → `iinf` to find the Exif item, → `iloc` to locate its
 * bytes, → `mdat` or `idat` for the payload). The returned block is what
 * `androidx.heifwriter.HeifWriter.addExifData()` expects: `"Exif  "` + TIFF header + IFDs.
 *
 * Why not `androidx.exifinterface`: as of 1.4.2 its HEIF Exif reader is unreliable — `getAttribute`
 * returns null for `Make` / `DateTime` / `latLong` on real-world libheif/Pixel HEIF files.
 * Bypassing it for HEIF reads is the only way to preserve metadata round-trip.
 *
 * The result type distinguishes three states so the caller can fail closed on unsupported input
 * instead of silently dropping metadata: [Result.NoExif] (legitimate — source genuinely lacks
 * EXIF), [Result.Extracted] (success), [Result.Unsupported] (Exif item is present but we can't
 * extract it safely — e.g. construction_method=2, missing idat, malformed offsets).
 */
@Reusable
class HeifExifExtractor @Inject constructor() {

    sealed class Result {
        /** Source HEIF has no EXIF item. Compressing without EXIF is the correct outcome. */
        object NoExif : Result()

        /** Successfully extracted the JPEG-APP1-form bytes (`"Exif  "` + TIFF). */
        data class Extracted(val bytes: ByteArray) : Result()

        /**
         * An EXIF item is present in the source but we couldn't extract it safely. Compressing
         * the file without EXIF would silently strip the user's date/location/camera metadata,
         * so the caller should abort rather than proceed.
         */
        data class Unsupported(val reason: String) : Result()
    }

    fun extractExifBlock(sourceHeic: File): Result {
        return try {
            RandomAccessFile(sourceHeic, "r").use { raf -> parse(raf) }
        } catch (e: Exception) {
            log(TAG, WARN) { "extractExifBlock failed for ${sourceHeic.path}: ${e.message}" }
            Result.Unsupported("IO error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parse(raf: RandomAccessFile): Result {
        val fileSize = raf.length()

        val meta = findTopLevelBox(raf, 0L, fileSize, BOX_META)
            ?: return Result.Unsupported("missing meta box")

        // meta is a FullBox: 4 bytes version+flags at the start.
        val metaPayloadStart = meta.payloadStart + 4
        val metaPayloadEnd = meta.payloadEnd

        val iinf = findChildBox(raf, metaPayloadStart, metaPayloadEnd, BOX_IINF)
            ?: return Result.Unsupported("missing iinf box")
        val iloc = findChildBox(raf, metaPayloadStart, metaPayloadEnd, BOX_ILOC)
            ?: return Result.Unsupported("missing iloc box")
        // idat is optional — only required when construction_method=1 is used.
        val idat = findChildBox(raf, metaPayloadStart, metaPayloadEnd, BOX_IDAT)

        val exifItemId = when (val r = findItemIdByType(raf, iinf, ITEM_TYPE_EXIF)) {
            is ItemLookup.Found -> r.itemId
            ItemLookup.NotPresent -> {
                log(TAG, VERBOSE) { "No Exif item in HEIF" }
                return Result.NoExif
            }
            is ItemLookup.ParseError -> return Result.Unsupported("iinf: ${r.reason}")
        }

        val location = readItemLocation(raf, iloc, exifItemId)
            ?: return Result.Unsupported("iloc has no entry for Exif item $exifItemId")

        val itemBytes = readItemBytes(raf, location, idat)
            ?: return Result.Unsupported("could not read Exif item bytes (construction_method=${location.constructionMethod})")

        // The Exif item payload begins with a 4-byte exif_tiff_header_offset, then the EXIF data.
        if (itemBytes.size < 4) {
            return Result.Unsupported("Exif item shorter than 4-byte tiff_header_offset prefix")
        }
        val rawOffset = ((itemBytes[0].toInt() and 0xff) shl 24) or
            ((itemBytes[1].toInt() and 0xff) shl 16) or
            ((itemBytes[2].toInt() and 0xff) shl 8) or
            (itemBytes[3].toInt() and 0xff)
        val tiffStart = 4 + rawOffset
        if (tiffStart < 4 || tiffStart >= itemBytes.size) {
            return Result.Unsupported("Exif tiff_header_offset $rawOffset out of bounds")
        }

        val tiffLen = itemBytes.size - tiffStart
        if (tiffLen < 8) {
            return Result.Unsupported("Exif TIFF segment too short ($tiffLen bytes)")
        }
        val tiff = itemBytes.copyOfRange(tiffStart, itemBytes.size)

        // HeifWriter.addExifData wants JPEG APP1 form: "Exif  " + TIFF. The TIFF data may either
        // be that already (pillow-heif's quirk) or start directly with "II*" / "MM*".
        return when {
            tiff.size >= 6 && isExifMarker(tiff) -> Result.Extracted(tiff)
            tiff.size >= 4 && isTiffHeader(tiff) -> {
                val out = ByteArray(6 + tiff.size)
                EXIF_MARKER.copyInto(out, 0)
                tiff.copyInto(out, 6)
                Result.Extracted(out)
            }
            else -> Result.Unsupported("Exif bytes don't start with 'Exif  ' marker or TIFF header")
        }
    }

    /** Reads and concatenates all extents of an item, resolving construction_method. */
    private fun readItemBytes(raf: RandomAccessFile, location: ItemLocation, idat: BoxRange?): ByteArray? {
        when (location.constructionMethod) {
            CONSTRUCTION_FILE_OFFSET -> Unit
            CONSTRUCTION_IDAT_OFFSET -> {
                if (idat == null) {
                    log(TAG, WARN) { "Exif item uses idat construction but no idat box present" }
                    return null
                }
            }
            CONSTRUCTION_ITEM_OFFSET -> {
                log(TAG, WARN) { "Exif item uses item_offset construction (method 2); not supported" }
                return null
            }
            else -> {
                log(TAG, WARN) { "Unknown construction_method ${location.constructionMethod}; not supported" }
                return null
            }
        }

        val totalLen = location.extents.sumOf { it.length }
        if (totalLen <= 0L || totalLen > MAX_REASONABLE_EXIF_SIZE) {
            log(TAG, WARN) { "Exif item length $totalLen out of bounds (max $MAX_REASONABLE_EXIF_SIZE)" }
            return null
        }
        val out = ByteArray(totalLen.toInt())
        var written = 0
        for (extent in location.extents) {
            val source = when (location.constructionMethod) {
                CONSTRUCTION_FILE_OFFSET -> location.baseOffset + extent.offset
                CONSTRUCTION_IDAT_OFFSET -> idat!!.payloadStart + location.baseOffset + extent.offset
                else -> return null
            }
            if (source < 0 || source + extent.length > raf.length()) {
                log(TAG, WARN) { "Exif extent $source..${source + extent.length} out of file bounds" }
                return null
            }
            raf.seek(source)
            raf.readFully(out, written, extent.length.toInt())
            written += extent.length.toInt()
        }
        return out
    }

    private data class BoxRange(val payloadStart: Long, val payloadEnd: Long)
    private data class BoxHeader(val type: Int, val payloadStart: Long, val boxEnd: Long)
    private data class Extent(val offset: Long, val length: Long)
    private data class ItemLocation(
        val constructionMethod: Int,
        val baseOffset: Long,
        val extents: List<Extent>,
    )

    private fun findTopLevelBox(raf: RandomAccessFile, start: Long, end: Long, type: Int): BoxRange? {
        var pos = start
        while (pos < end) {
            val h = readBoxHeader(raf, pos, end, allowExtendsToEof = true) ?: return null
            if (h.type == type) return BoxRange(h.payloadStart, h.boxEnd)
            pos = h.boxEnd
        }
        return null
    }

    private fun findChildBox(raf: RandomAccessFile, start: Long, end: Long, type: Int): BoxRange? {
        var pos = start
        while (pos < end) {
            val h = readBoxHeader(raf, pos, end, allowExtendsToEof = false) ?: return null
            if (h.type == type) return BoxRange(h.payloadStart, h.boxEnd)
            pos = h.boxEnd
        }
        return null
    }

    private fun readBoxHeader(
        raf: RandomAccessFile,
        offset: Long,
        parentEnd: Long,
        allowExtendsToEof: Boolean,
    ): BoxHeader? {
        if (offset + 8 > parentEnd) return null
        raf.seek(offset)
        val sizeRaw = raf.readInt().toLong() and 0xffffffffL
        val type = raf.readInt()

        val payloadStart: Long
        val boxEnd: Long
        when {
            sizeRaw == 1L -> {
                if (offset + 16 > parentEnd) return null
                val largesize = raf.readLong()
                if (largesize < 16L) return null
                payloadStart = offset + 16
                boxEnd = offset + largesize
            }
            sizeRaw == 0L -> {
                if (!allowExtendsToEof) return null
                payloadStart = offset + 8
                boxEnd = parentEnd
            }
            sizeRaw < 8L -> return null
            else -> {
                payloadStart = offset + 8
                boxEnd = offset + sizeRaw
            }
        }
        if (boxEnd > parentEnd) return null
        return BoxHeader(type, payloadStart, boxEnd)
    }

    /**
     * Tri-state result for iinf item lookup so callers can distinguish "no matching item" (a
     * legitimate state) from "iinf or one of its infe entries failed to parse" (we must not claim
     * 'no EXIF' for that — it should propagate as Unsupported).
     */
    private sealed class ItemLookup {
        object NotPresent : ItemLookup()
        data class Found(val itemId: Int) : ItemLookup()
        data class ParseError(val reason: String) : ItemLookup()
    }

    /**
     * Walks the `iinf` `infe` entries looking for the item whose item_type equals [itemTypeFourcc]
     * (e.g. `"Exif"`). Returns [ItemLookup.Found] on success, [ItemLookup.NotPresent] when iinf
     * is fully parsed and no entry matches, or [ItemLookup.ParseError] when any sub-box can't be
     * read or an infe with an unsupported version is encountered (it could have been the Exif
     * item — we can't tell, so fail closed).
     */
    private fun findItemIdByType(raf: RandomAccessFile, iinf: BoxRange, itemTypeFourcc: Int): ItemLookup {
        // iinf is a FullBox. Version 0: 2-byte entry_count. Version 1+: 4-byte entry_count.
        raf.seek(iinf.payloadStart)
        val versionAndFlags = raf.readInt()
        val version = (versionAndFlags ushr 24) and 0xff

        val entryCount: Int
        val entriesStart: Long
        if (version == 0) {
            entryCount = raf.readShort().toInt() and 0xffff
            entriesStart = iinf.payloadStart + 6
        } else {
            entryCount = raf.readInt()
            entriesStart = iinf.payloadStart + 8
        }
        // A 32-bit count reads negative for corrupt values >= 2^31. Failing open here would skip
        // the entry loop entirely and report "no Exif item" — silently stripping metadata.
        if (entryCount < 0) return ItemLookup.ParseError("implausible iinf entry count $entryCount")

        var pos = entriesStart
        var seen = 0
        while (seen < entryCount && pos < iinf.payloadEnd) {
            val h = readBoxHeader(raf, pos, iinf.payloadEnd, allowExtendsToEof = false)
                ?: return ItemLookup.ParseError("malformed box at offset $pos inside iinf")
            if (h.type == BOX_INFE) {
                when (val res = readInfeItem(raf, h, itemTypeFourcc)) {
                    is ItemLookup.Found -> return res
                    is ItemLookup.ParseError -> return res
                    ItemLookup.NotPresent -> Unit // not a match, keep scanning
                }
            }
            pos = h.boxEnd
            seen++
        }
        if (seen < entryCount) {
            // Truncated iinf: it declared more entries than its box holds. One of the missing
            // entries could have been the Exif item — we can't tell, so fail closed.
            return ItemLookup.ParseError("iinf truncated: saw $seen of $entryCount declared entries")
        }
        return ItemLookup.NotPresent
    }

    /**
     * Reads an `infe` box and reports whether it describes the [itemTypeFourcc] we want.
     * Tri-state so an unsupported infe version (which could conceal the Exif item we're after)
     * propagates as a parse error instead of being silently skipped.
     */
    private fun readInfeItem(raf: RandomAccessFile, infe: BoxHeader, itemTypeFourcc: Int): ItemLookup {
        raf.seek(infe.payloadStart)
        val versionAndFlags = raf.readInt()
        val version = (versionAndFlags ushr 24) and 0xff

        val itemId: Int
        val typeOffset: Long
        when (version) {
            2 -> {
                itemId = raf.readShort().toInt() and 0xffff
                raf.skipBytes(2) // item_protection_index
                typeOffset = infe.payloadStart + 4 + 2 + 2
            }
            3 -> {
                itemId = raf.readInt()
                raf.skipBytes(2) // item_protection_index
                typeOffset = infe.payloadStart + 4 + 4 + 2
            }
            else -> return ItemLookup.ParseError("unsupported infe version $version")
        }
        raf.seek(typeOffset)
        val itemType = raf.readInt()
        return if (itemType == itemTypeFourcc) ItemLookup.Found(itemId) else ItemLookup.NotPresent
    }

    /**
     * Walks the `iloc` entries to find [targetItemId]. Returns its construction_method, base_offset,
     * and the full extent list. Caller resolves extents to actual bytes via [readItemBytes].
     */
    private fun readItemLocation(raf: RandomAccessFile, iloc: BoxRange, targetItemId: Int): ItemLocation? {
        raf.seek(iloc.payloadStart)
        val versionAndFlags = raf.readInt()
        val version = (versionAndFlags ushr 24) and 0xff

        val sizeByte = raf.readByte().toInt() and 0xff
        val offsetSize = (sizeByte ushr 4) and 0x0f
        val lengthSize = sizeByte and 0x0f
        val sizeByte2 = raf.readByte().toInt() and 0xff
        val baseOffsetSize = (sizeByte2 ushr 4) and 0x0f
        val indexSize = if (version >= 1) sizeByte2 and 0x0f else 0

        val itemCount: Int = if (version < 2) {
            raf.readShort().toInt() and 0xffff
        } else {
            raf.readInt()
        }

        repeat(itemCount) {
            val itemId: Int = if (version < 2) {
                raf.readShort().toInt() and 0xffff
            } else {
                raf.readInt()
            }
            // construction_method only exists in v1/v2. It's the lower 4 bits of a 2-byte field
            // (upper 12 bits are reserved).
            val constructionMethod: Int = if (version in 1..2) {
                raf.readShort().toInt() and 0x000f
            } else {
                CONSTRUCTION_FILE_OFFSET // v0 implicitly uses file_offset
            }
            raf.skipBytes(2) // data_reference_index
            val baseOffset = readUInt(raf, baseOffsetSize)
            val extentCount = raf.readShort().toInt() and 0xffff
            val extents = mutableListOf<Extent>()
            repeat(extentCount) {
                if (version in 1..2 && indexSize > 0) {
                    readUInt(raf, indexSize) // extent_index (only used by construction_method=2)
                }
                val extentOffset = readUInt(raf, offsetSize)
                val extentLength = readUInt(raf, lengthSize)
                extents.add(Extent(extentOffset, extentLength))
            }
            if (itemId == targetItemId && extents.isNotEmpty()) {
                return ItemLocation(constructionMethod, baseOffset, extents)
            }
        }
        return null
    }

    private fun readUInt(raf: RandomAccessFile, byteSize: Int): Long = when (byteSize) {
        0 -> 0L
        1 -> raf.readByte().toLong() and 0xffL
        2 -> raf.readShort().toLong() and 0xffffL
        4 -> raf.readInt().toLong() and 0xffffffffL
        8 -> raf.readLong()
        else -> throw IllegalStateException("Unsupported size field: $byteSize bytes")
    }

    private fun isExifMarker(b: ByteArray): Boolean {
        return b[0] == 'E'.code.toByte() &&
            b[1] == 'x'.code.toByte() &&
            b[2] == 'i'.code.toByte() &&
            b[3] == 'f'.code.toByte() &&
            b[4].toInt() == 0 &&
            b[5].toInt() == 0
    }

    /** True if the bytes start with a valid TIFF header (`II*\x00` or `MM\x00*`). */
    private fun isTiffHeader(b: ByteArray): Boolean {
        val little = b[0] == 'I'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2].toInt() == 0x2a && b[3].toInt() == 0
        val big = b[0] == 'M'.code.toByte() && b[1] == 'M'.code.toByte() &&
            b[2].toInt() == 0 && b[3].toInt() == 0x2a
        return little || big
    }

    companion object {
        private val TAG = logTag("Squeezer", "Image", "Encoder", "Heif", "ExifExtract")

        private const val CONSTRUCTION_FILE_OFFSET = 0
        private const val CONSTRUCTION_IDAT_OFFSET = 1
        private const val CONSTRUCTION_ITEM_OFFSET = 2

        // A sanity guard against malformed iloc that would otherwise have us allocate gigabytes.
        // Real-world Exif items are well under 1 MB; 8 MB leaves room for anything legitimate.
        private const val MAX_REASONABLE_EXIF_SIZE = 8L * 1024 * 1024

        private val EXIF_MARKER = byteArrayOf('E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0, 0)

        private fun fourcc(s: String): Int {
            require(s.length == 4)
            return ((s[0].code and 0xff) shl 24) or
                ((s[1].code and 0xff) shl 16) or
                ((s[2].code and 0xff) shl 8) or
                (s[3].code and 0xff)
        }

        private val BOX_META = fourcc("meta")
        private val BOX_IINF = fourcc("iinf")
        private val BOX_ILOC = fourcc("iloc")
        private val BOX_INFE = fourcc("infe")
        private val BOX_IDAT = fourcc("idat")
        private val ITEM_TYPE_EXIF = fourcc("Exif")
    }
}
