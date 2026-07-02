package eu.darken.sdmse.squeezer.core.processor

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Reads the display transform properties (`irot`/`imir`) associated with a HEIF/HEIC file's
 * primary image item by walking `meta` → `pitm` (primary item id), `iprp`/`ipco` (the ordered
 * property list) and `ipma` (item→property associations).
 *
 * Why this matters: our re-encode path decodes via `BitmapFactory` — which (verified on-device)
 * returns the STORED pixels without applying `irot` — and `HeifWriter` writes no transform
 * properties by itself. Without carrying the source `irot` into the output, a rotated photo
 * (e.g. any iPhone portrait shot) would permanently lose its container rotation. The caller
 * uses this result to reproduce the source transform on the output, or to skip the file when
 * that isn't possible (fail closed — a mis-rotated photo is unrecoverable once the original
 * is deleted).
 *
 * Parse anomalies (unknown versions, out-of-bounds indices, duplicate transforms) always
 * surface as [Result.Unsupported], never as "no transform".
 */
@Reusable
class HeifTransformInspector @Inject constructor() {

    sealed class Result {
        /** Primary item carries no rotation/mirror property (irot angle 0 counts as none). */
        object NoTransform : Result()

        /**
         * Primary item carries exactly one `irot` and no `imir`.
         * [angleCcw] is the raw irot angle field: counter-clockwise multiples of 90, in 1..3.
         * [storedWidth]/[storedHeight] are the primary item's `ispe` dimensions (pre-transform).
         */
        data class Rotated(val angleCcw: Int, val storedWidth: Long, val storedHeight: Long) : Result()

        /** Primary item carries an `imir` mirror — not reproducible via HeifWriter. */
        object Mirrored : Result()

        /** Transform state could not be determined safely. */
        data class Unsupported(val reason: String) : Result()
    }

    fun inspect(sourceHeic: File): Result {
        return try {
            RandomAccessFile(sourceHeic, "r").use { raf -> parse(raf) }
        } catch (e: Exception) {
            log(TAG, WARN) { "inspect failed for ${sourceHeic.path}: ${e.message}" }
            Result.Unsupported("IO error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun parse(raf: RandomAccessFile): Result {
        val fileSize = raf.length()

        val meta = findBox(raf, 0L, fileSize, BOX_META, allowExtendsToEof = true)
            ?: return Result.Unsupported("missing meta box")

        // Every container is scanned fully before use: a malformed sibling box anywhere inside
        // meta or iprp must surface as Unsupported, never be mistaken for "box not present"
        // (which would fail open as NoTransform).
        // meta is a FullBox: 4 bytes version+flags at the start.
        val metaChildren = scanChildren(raf, meta.payloadStart + 4, meta.payloadEnd)
            ?: return Result.Unsupported("malformed meta box")

        val pitm = metaChildren.singleOrNull { it.type == BOX_PITM }
            ?: return Result.Unsupported("expected exactly one pitm box")
        val primaryItemId = readPitm(raf, pitm)
            ?: return Result.Unsupported("unsupported pitm version")

        val iprps = metaChildren.filter { it.type == BOX_IPRP }
        // No property container at all: nothing can carry a transform.
        if (iprps.isEmpty()) return Result.NoTransform
        val iprp = iprps.singleOrNull()
            ?: return Result.Unsupported("multiple iprp boxes")

        val iprpChildren = scanChildren(raf, iprp.payloadStart, iprp.boxEnd)
            ?: return Result.Unsupported("malformed iprp box")
        val ipcos = iprpChildren.filter { it.type == BOX_IPCO }
        val ipmas = iprpChildren.filter { it.type == BOX_IPMA }
        if (ipcos.isEmpty() && ipmas.isEmpty()) return Result.NoTransform
        val ipco = ipcos.singleOrNull()
            ?: return Result.Unsupported("expected exactly one ipco box, got ${ipcos.size}")

        val properties = scanChildren(raf, ipco.payloadStart, ipco.boxEnd)
            ?: return Result.Unsupported("malformed ipco box")

        // Associations for an item may live in any of several ipma boxes; all must parse.
        var propertyIndices: List<Int>? = null
        for (ipma in ipmas) {
            when (val lookup = readIpmaAssociations(raf, ipma, primaryItemId)) {
                is IpmaLookup.Found -> {
                    if (propertyIndices != null) {
                        return Result.Unsupported("primary item associated in multiple ipma boxes")
                    }
                    propertyIndices = lookup.propertyIndices
                }
                IpmaLookup.NotPresent -> Unit
                is IpmaLookup.ParseError -> return Result.Unsupported("ipma: ${lookup.reason}")
            }
        }
        if (propertyIndices == null) return Result.NoTransform

        var rotationAngle: Int? = null
        var mirrored = false
        var storedWidth = -1L
        var storedHeight = -1L
        for (index in propertyIndices) {
            // ipma property_index is 1-based; 0 means "no property" and is skipped per spec.
            if (index == 0) continue
            val property = properties.getOrNull(index - 1)
                ?: return Result.Unsupported("ipma references property $index but ipco has ${properties.size}")
            when (property.type) {
                BOX_IROT -> {
                    if (rotationAngle != null) return Result.Unsupported("multiple irot properties on primary item")
                    if (property.boxEnd - property.payloadStart < 1) {
                        return Result.Unsupported("irot box too short")
                    }
                    raf.seek(property.payloadStart)
                    rotationAngle = raf.readByte().toInt() and 0x03
                }
                BOX_IMIR -> mirrored = true
                BOX_ISPE -> {
                    if (property.boxEnd - property.payloadStart < 12) {
                        return Result.Unsupported("ispe box too short")
                    }
                    raf.seek(property.payloadStart + 4) // skip version+flags
                    storedWidth = raf.readInt().toLong() and 0xffffffffL
                    storedHeight = raf.readInt().toLong() and 0xffffffffL
                }
                else -> Unit
            }
        }

        return when {
            mirrored -> Result.Mirrored
            rotationAngle == null || rotationAngle == 0 -> Result.NoTransform
            storedWidth <= 0L || storedHeight <= 0L ->
                Result.Unsupported("rotated primary item without valid ispe dimensions")
            else -> Result.Rotated(angleCcw = rotationAngle, storedWidth = storedWidth, storedHeight = storedHeight)
        }
    }

    private fun readPitm(raf: RandomAccessFile, pitm: BoxHeader): Int? {
        if (pitm.boxEnd - pitm.payloadStart < 6) return null
        raf.seek(pitm.payloadStart)
        val versionAndFlags = raf.readInt()
        return when ((versionAndFlags ushr 24) and 0xff) {
            0 -> raf.readShort().toInt() and 0xffff
            1 -> {
                if (pitm.boxEnd - pitm.payloadStart < 8) return null
                raf.readInt()
            }
            else -> null
        }
    }

    /** Returns a container's direct children in order (1-based indexing per ipma), or null on malformed data. */
    private fun scanChildren(raf: RandomAccessFile, start: Long, end: Long): List<BoxHeader>? {
        val children = mutableListOf<BoxHeader>()
        var pos = start
        while (pos < end) {
            val h = readBoxHeader(raf, pos, end, allowExtendsToEof = false) ?: return null
            children.add(h)
            pos = h.boxEnd
            if (children.size > MAX_PROPERTIES) return null
        }
        return children
    }

    private sealed class IpmaLookup {
        object NotPresent : IpmaLookup()
        data class Found(val propertyIndices: List<Int>) : IpmaLookup()
        data class ParseError(val reason: String) : IpmaLookup()
    }

    private fun readIpmaAssociations(raf: RandomAccessFile, ipma: BoxHeader, targetItemId: Int): IpmaLookup {
        val end = ipma.boxEnd
        if (end - ipma.payloadStart < 8) return IpmaLookup.ParseError("box too short")
        raf.seek(ipma.payloadStart)
        val versionAndFlags = raf.readInt()
        val version = (versionAndFlags ushr 24) and 0xff
        if (version > 1) return IpmaLookup.ParseError("unsupported version $version")
        val flags = versionAndFlags and 0xffffff
        val wideIndices = (flags and 0x1) != 0

        val entryCount = raf.readInt().toLong() and 0xffffffffL
        if (entryCount > MAX_IPMA_ENTRIES) return IpmaLookup.ParseError("implausible entry count $entryCount")

        val itemIdSize = if (version < 1) 2 else 4
        val indexSize = if (wideIndices) 2 else 1
        var found: List<Int>? = null
        repeat(entryCount.toInt()) {
            if (raf.filePointer + itemIdSize + 1 > end) return IpmaLookup.ParseError("truncated ipma entries")
            val itemId = if (version < 1) {
                raf.readShort().toInt() and 0xffff
            } else {
                raf.readInt()
            }
            val associationCount = raf.readByte().toInt() and 0xff
            if (raf.filePointer + associationCount.toLong() * indexSize > end) {
                return IpmaLookup.ParseError("truncated ipma associations")
            }
            val indices = mutableListOf<Int>()
            repeat(associationCount) {
                val index = if (wideIndices) {
                    raf.readShort().toInt() and 0x7fff // top bit is essential flag
                } else {
                    raf.readByte().toInt() and 0x7f
                }
                indices.add(index)
            }
            if (itemId == targetItemId) {
                if (found != null) return IpmaLookup.ParseError("duplicate ipma entry for item $itemId")
                found = indices
            }
        }
        return found?.let { IpmaLookup.Found(it) } ?: IpmaLookup.NotPresent
    }

    private data class BoxRange(val payloadStart: Long, val payloadEnd: Long)
    private data class BoxHeader(val type: Int, val payloadStart: Long, val boxEnd: Long)

    private fun findBox(
        raf: RandomAccessFile,
        start: Long,
        end: Long,
        type: Int,
        allowExtendsToEof: Boolean = false,
    ): BoxRange? {
        var pos = start
        while (pos < end) {
            val h = readBoxHeader(raf, pos, end, allowExtendsToEof) ?: return null
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

    companion object {
        private val TAG = logTag("Squeezer", "Image", "Encoder", "Heif", "Transform")

        // Sanity bounds against malformed files driving unbounded loops/allocations.
        private const val MAX_PROPERTIES = 10_000
        private const val MAX_IPMA_ENTRIES = 100_000L

        private fun fourcc(s: String): Int {
            require(s.length == 4)
            return ((s[0].code and 0xff) shl 24) or
                ((s[1].code and 0xff) shl 16) or
                ((s[2].code and 0xff) shl 8) or
                (s[3].code and 0xff)
        }

        private val BOX_META = fourcc("meta")
        private val BOX_PITM = fourcc("pitm")
        private val BOX_IPRP = fourcc("iprp")
        private val BOX_IPCO = fourcc("ipco")
        private val BOX_IPMA = fourcc("ipma")
        private val BOX_IROT = fourcc("irot")
        private val BOX_IMIR = fourcc("imir")
        private val BOX_ISPE = fourcc("ispe")
    }
}
