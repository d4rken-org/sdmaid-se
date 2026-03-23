package eu.darken.sdmse.deduplicator.core.scanner.media.audiohash

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class AudioFingerprinter @Inject constructor(
    private val fingerprintCalculator: FingerprintCalculator,
) {

    data class Result(
        val fingerprint: FingerprintCalculator.Result,
        val durationMs: Long,
    ) {
        fun similarityTo(other: Result): Double = fingerprint.similarityTo(other.fingerprint)
    }

    /**
     * Extract audio from [filePath] and compute a fingerprint.
     * Returns null if the file has no audio track or cannot be decoded.
     */
    fun fingerprint(filePath: String): Result? {
        val totalStart = System.currentTimeMillis()

        val openStart = System.currentTimeMillis()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(filePath)
        } catch (e: IOException) {
            log(TAG, WARN) { "Failed to open $filePath: $e" }
            return null
        }
        val openMs = System.currentTimeMillis() - openStart

        try {
            val audioTrackIndex = findAudioTrack(extractor) ?: run {
                log(TAG, VERBOSE) { "No audio track in $filePath" }
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
            val pcmSamples = decodeAudioToPcm(extractor, mime, format, sampleRate, channelCount)
                ?: return null
            val decodeMs = System.currentTimeMillis() - decodeStart

            val fpStart = System.currentTimeMillis()
            val fingerprint = fingerprintCalculator.calculate(pcmSamples, sampleRate)
                ?: run {
                    log(TAG, VERBOSE) { "Too few samples for fingerprint from $filePath" }
                    return null
                }
            val fpMs = System.currentTimeMillis() - fpStart

            val totalMs = System.currentTimeMillis() - totalStart
            val fileName = filePath.substringAfterLast('/')
            log(TAG) { "Audio [$fileName] open=${openMs}ms decode=${decodeMs}ms fp=${fpMs}ms total=${totalMs}ms" }

            return Result(
                fingerprint = fingerprint,
                durationMs = durationMs,
            )
        } finally {
            extractor.release()
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

    private fun decodeAudioToPcm(
        extractor: MediaExtractor,
        mime: String,
        format: MediaFormat,
        sampleRate: Int,
        channelCount: Int,
    ): ShortArray? {
        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: IOException) {
            log(TAG, WARN) { "No decoder for $mime: $e" }
            return null
        }

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            // Collect ~10 seconds of mono samples at the original sample rate
            val maxSamples = sampleRate * MAX_DURATION_SECONDS
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
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
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
        private const val MAX_DURATION_SECONDS = 5
        private val TAG = logTag("Deduplicator", "Sleuth", "Media", "AudioFingerprinter")
    }
}
