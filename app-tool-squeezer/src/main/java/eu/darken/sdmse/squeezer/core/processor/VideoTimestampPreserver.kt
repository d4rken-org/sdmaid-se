package eu.darken.sdmse.squeezer.core.processor

import android.media.MediaMetadataRetriever
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Reads the source video's `mvhd` `creation_time` + `modification_time` so the same
 * values can be injected into the Media3 `Transformer` output. Without this, gallery
 * apps reorder compressed videos: `MediaStore.DATE_TAKEN` is sourced from `mvhd`
 * `creation_time`, and Media3's muxer writes "now" by default.
 *
 * Two extraction paths in priority order:
 * 1. Atom walker on the source MP4 — gives both `creation_time` and `modification_time` directly.
 * 2. `MediaMetadataRetriever.METADATA_KEY_DATE` fallback — only exposes one date, so
 *    `modification_time` is set equal to `creation_time` for that path.
 *
 * Best-effort: extraction failures return `null` and the caller skips preservation.
 */
@Reusable
class VideoTimestampPreserver @Inject constructor() {

    data class TimestampData(
        val creationTimeMp4Seconds: Long,
        val modificationTimeMp4Seconds: Long,
    )

    fun extract(file: File): TimestampData? {
        extractFromMvhd(file)?.let { return it }
        return extractFromMmr(file)
    }

    internal fun parseMetadataDate(value: String?): Instant? {
        // AOSP returns ISO 8601 *basic* format (no separators):
        // e.g. "20260510T123456.000Z". `Instant.parse()` requires the extended format
        // and would throw, so we use a dedicated formatter.
        if (value.isNullOrBlank()) return null
        return try {
            BASIC_ISO_FORMATTER.parse(value, Instant::from)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    internal fun extractFromMvhd(file: File): TimestampData? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = raf.length()
                val moov = findTopLevelBox(raf, 0L, fileSize, BOX_MOOV) ?: return null
                val mvhd = findChildBox(raf, moov, BOX_MVHD) ?: return null
                readMvhdTimes(raf, mvhd)
            }
        } catch (e: IOException) {
            log(TAG, WARN) { "mvhd extraction failed for ${file.path}: ${e.message}" }
            null
        } catch (e: Exception) {
            log(TAG, WARN) { "mvhd extraction failed for ${file.path}: ${e.message}" }
            null
        }
    }

    private fun extractFromMmr(file: File): TimestampData? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val date = parseMetadataDate(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ) ?: return null
                val mp4 = date.epochSecond + MP4_EPOCH_OFFSET_SECONDS
                if (!isPlausible(mp4)) return null
                TimestampData(creationTimeMp4Seconds = mp4, modificationTimeMp4Seconds = mp4)
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "MMR extraction failed for ${file.path}: ${e.message}" }
            null
        }
    }

    private data class BoxRange(val payloadStart: Long, val payloadEnd: Long)

    private data class BoxHeader(val type: Int, val payloadStart: Long, val boxEnd: Long)

    private fun findTopLevelBox(raf: RandomAccessFile, start: Long, end: Long, type: Int): BoxRange? {
        var pos = start
        while (pos < end) {
            val header = readBoxHeader(raf, pos, end, allowExtendsToEof = true) ?: return null
            if (header.type == type) {
                return BoxRange(payloadStart = header.payloadStart, payloadEnd = header.boxEnd)
            }
            pos = header.boxEnd
        }
        return null
    }

    private fun findChildBox(raf: RandomAccessFile, parent: BoxRange, type: Int): BoxRange? {
        var pos = parent.payloadStart
        while (pos < parent.payloadEnd) {
            val header = readBoxHeader(raf, pos, parent.payloadEnd, allowExtendsToEof = false) ?: return null
            if (header.type == type) {
                return BoxRange(payloadStart = header.payloadStart, payloadEnd = header.boxEnd)
            }
            pos = header.boxEnd
        }
        return null
    }

    private fun readBoxHeader(
        raf: RandomAccessFile,
        offset: Long,
        parentEnd: Long,
        allowExtendsToEof: Boolean,
    ): BoxHeader? {
        if (offset + MIN_HEADER_SIZE > parentEnd) return null

        raf.seek(offset)
        val sizeRaw = raf.readInt().toLong() and 0xffffffffL
        val type = raf.readInt()

        val payloadStart: Long
        val boxEnd: Long
        when {
            sizeRaw == 1L -> {
                if (offset + LARGESIZE_HEADER_SIZE > parentEnd) return null
                val largesize = raf.readLong()
                if (largesize < LARGESIZE_HEADER_SIZE) return null
                payloadStart = offset + LARGESIZE_HEADER_SIZE
                boxEnd = offset + largesize
            }
            sizeRaw == 0L -> {
                // "extends to EOF" is only valid at top level per ISO/IEC 14496-12.
                if (!allowExtendsToEof) return null
                payloadStart = offset + MIN_HEADER_SIZE
                boxEnd = parentEnd
            }
            sizeRaw < MIN_HEADER_SIZE -> return null
            else -> {
                payloadStart = offset + MIN_HEADER_SIZE
                boxEnd = offset + sizeRaw
            }
        }

        if (boxEnd > parentEnd) return null
        if (payloadStart > boxEnd) return null

        return BoxHeader(type = type, payloadStart = payloadStart, boxEnd = boxEnd)
    }

    private fun readMvhdTimes(raf: RandomAccessFile, mvhd: BoxRange): TimestampData? {
        val payloadSize = mvhd.payloadEnd - mvhd.payloadStart
        if (payloadSize < FULLBOX_HEADER_SIZE) return null

        raf.seek(mvhd.payloadStart)
        val versionAndFlags = raf.readInt()
        val version = (versionAndFlags ushr 24) and 0xff

        val creation: Long
        val modification: Long
        when (version) {
            0 -> {
                if (payloadSize < FULLBOX_HEADER_SIZE + 8) return null
                creation = raf.readInt().toLong() and 0xffffffffL
                modification = raf.readInt().toLong() and 0xffffffffL
            }
            1 -> {
                if (payloadSize < FULLBOX_HEADER_SIZE + 16) return null
                creation = raf.readLong()
                modification = raf.readLong()
            }
            else -> return null
        }

        if (!isPlausible(creation)) return null
        return TimestampData(
            creationTimeMp4Seconds = creation,
            modificationTimeMp4Seconds = modification,
        )
    }

    private fun isPlausible(mp4Seconds: Long): Boolean = mp4Seconds > PLAUSIBILITY_FLOOR_SECONDS

    companion object {
        internal const val MP4_EPOCH_OFFSET_SECONDS = 2_082_844_800L
        private const val PLAUSIBILITY_FLOOR_SECONDS = 86_400L
        private const val MIN_HEADER_SIZE = 8L
        private const val LARGESIZE_HEADER_SIZE = 16L
        private const val FULLBOX_HEADER_SIZE = 4
        private val TAG = logTag("Squeezer", "VideoTimestampPreserver")

        private val BASIC_ISO_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss[.SSS]X")
                .withZone(ZoneOffset.UTC)

        private fun fourcc(s: String): Int {
            require(s.length == 4)
            return ((s[0].code and 0xff) shl 24) or
                ((s[1].code and 0xff) shl 16) or
                ((s[2].code and 0xff) shl 8) or
                (s[3].code and 0xff)
        }

        private val BOX_MOOV = fourcc("moov")
        private val BOX_MVHD = fourcc("mvhd")
    }
}
