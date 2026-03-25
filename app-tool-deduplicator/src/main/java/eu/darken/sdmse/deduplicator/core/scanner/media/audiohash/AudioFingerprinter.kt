package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.GatewayMediaDataSource
import eu.darken.sdmse.common.files.GatewaySwitch
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class AudioFingerprinter @Inject constructor(
    private val fingerprintCalculator: FingerprintCalculator,
    private val gatewaySwitch: GatewaySwitch,
) {

    data class Result(
        val fingerprints: List<FingerprintCalculator.Result>,
        val durationMs: Long,
    ) {
        fun similarityTo(other: Result): Double {
            if (fingerprints.isEmpty() || other.fingerprints.isEmpty()) return 0.0
            val pairs = minOf(fingerprints.size, other.fingerprints.size)
            val totalSim = (0 until pairs).sumOf { i -> fingerprints[i].similarityTo(other.fingerprints[i]) }
            return totalSim / pairs
        }
    }

    /**
     * Extract audio from [lookup] and compute fingerprints.
     * Uses the gateway abstraction for file access, supporting local, root, Shizuku, and SAF paths.
     *
     * For files >= 6 seconds: computes 3 fingerprints at 10%, 50%, 90% of duration.
     * For shorter files: computes 1 fingerprint from the start (up to 5 seconds).
     *
     * Returns null if the file has no audio track or cannot be decoded.
     */
    suspend fun fingerprint(lookup: APathLookup<*>): Result? {
        val totalStart = System.currentTimeMillis()
        val fileName = lookup.path.substringAfterLast('/')
        val mediaDataSource = GatewayMediaDataSource(gatewaySwitch.file(lookup.lookedUp, readWrite = false))
        val extractor = MediaExtractor()
        try {
            val openStart = System.currentTimeMillis()
            try {
                extractor.setDataSource(mediaDataSource)
            } catch (e: IOException) {
                log(TAG, WARN) { "Failed to open $fileName: $e" }
                return null
            }
            val openMs = System.currentTimeMillis() - openStart

            val audioTrackIndex = findAudioTrack(extractor) ?: run {
                log(TAG, VERBOSE) { "No audio track in $fileName" }
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            val durationMs = durationUs / 1000L
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val decodeStart = System.currentTimeMillis()
            val fingerprints = withAudioCodec(mime, format) { codec ->
                if (durationMs >= SHORT_FILE_THRESHOLD_MS) {
                    fingerprintMultiPosition(extractor, codec, sampleRate, channelCount, durationUs, fileName)
                } else {
                    fingerprintSinglePosition(extractor, codec, sampleRate, channelCount, fileName)
                }
            } ?: return null
            val decodeMs = System.currentTimeMillis() - decodeStart

            if (fingerprints.isEmpty()) {
                log(TAG, VERBOSE) { "No valid fingerprints from $fileName" }
                return null
            }

            val totalMs = System.currentTimeMillis() - totalStart
            log(TAG, VERBOSE) {
                "Audio [$fileName] ${fingerprints.size} segments, open=${openMs}ms decode=${decodeMs}ms total=${totalMs}ms"
            }

            return Result(
                fingerprints = fingerprints,
                durationMs = durationMs,
            )
        } finally {
            extractor.release()
            mediaDataSource.close()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun fingerprintSinglePosition(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sampleRate: Int,
        channelCount: Int,
        fileName: String,
    ): List<FingerprintCalculator.Result> {
        val maxSamples = sampleRate * MAX_SINGLE_DURATION_SECONDS
        val pcm = decodePcmSegment(extractor, codec, channelCount, maxSamples) ?: return emptyList()
        val fp = fingerprintCalculator.calculate(pcm, sampleRate) ?: run {
            log(TAG, VERBOSE) { "Too few samples for fingerprint from $fileName" }
            return emptyList()
        }
        return listOf(fp)
    }

    private fun fingerprintMultiPosition(
        extractor: MediaExtractor,
        codec: MediaCodec,
        sampleRate: Int,
        channelCount: Int,
        durationUs: Long,
        fileName: String,
    ): List<FingerprintCalculator.Result> {
        val maxSamples = sampleRate * SEGMENT_DURATION_SECONDS
        return FINGERPRINT_POSITIONS.toList().mapNotNull { pct ->
            val seekUs = (durationUs * pct).toLong()
            extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            codec.flush()

            val pcm = decodePcmSegment(extractor, codec, channelCount, maxSamples)
            if (pcm == null) {
                log(TAG, VERBOSE) { "No PCM at ${(pct * 100).toInt()}% of $fileName" }
                return@mapNotNull null
            }

            val fp = fingerprintCalculator.calculate(pcm, sampleRate)
            if (fp == null) {
                log(TAG, VERBOSE) { "Fingerprint failed at ${(pct * 100).toInt()}% of $fileName" }
            }
            fp
        }
    }

    private inline fun <T> withAudioCodec(
        mime: String,
        format: MediaFormat,
        block: (MediaCodec) -> T,
    ): T? {
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: IOException) {
            log(TAG, WARN) { "No decoder for $mime: $e" }
            return null
        }
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return block(codec)
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
    }

    private fun decodePcmSegment(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channelCount: Int,
        maxSamples: Int,
    ): ShortArray? {
        val monoSamples = ShortArray(maxSamples)
        var sampleCount = 0
        var inputDone = false
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        while (sampleCount < maxSamples) {
            // Feed input
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val bytesRead = extractor.readSampleData(inputBuffer, 0)
                    if (bytesRead < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, bytesRead, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Read output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    codec.releaseOutputBuffer(outputIndex, false)
                    break
                }

                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                outputBuffer.order(ByteOrder.nativeOrder())

                sampleCount = readPcmToMono(
                    outputBuffer,
                    channelCount,
                    monoSamples,
                    sampleCount,
                    maxSamples,
                )

                codec.releaseOutputBuffer(outputIndex, false)
            }
        }

        return if (sampleCount > 0) monoSamples.copyOf(sampleCount) else null
    }

    private fun readPcmToMono(
        buffer: ByteBuffer,
        channelCount: Int,
        output: ShortArray,
        startOffset: Int,
        maxSamples: Int,
    ): Int {
        val shortBuffer = buffer.asShortBuffer()
        val available = shortBuffer.remaining()
        var offset = startOffset

        if (channelCount == 1) {
            val toRead = minOf(available, maxSamples - offset)
            shortBuffer.get(output, offset, toRead)
            offset += toRead
        } else {
            // Downmix to mono by averaging channels
            val frames = available / channelCount
            for (i in 0 until frames) {
                if (offset >= maxSamples) break
                var sum = 0L
                for (ch in 0 until channelCount) {
                    sum += shortBuffer.get()
                }
                output[offset++] = (sum / channelCount).toInt().toShort()
            }
        }

        return offset
    }

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
        return try {
            getLong(key)
        } catch (_: Exception) {
            default
        }
    }

    companion object {
        private const val MAX_SINGLE_DURATION_SECONDS = 5
        private const val SEGMENT_DURATION_SECONDS = 2
        private const val SHORT_FILE_THRESHOLD_MS = 6_000L
        private val FINGERPRINT_POSITIONS = doubleArrayOf(0.10, 0.50, 0.90)
        private val TAG = logTag("Deduplicator", "Sleuth", "Media", "AudioFingerprinter")
    }
}
