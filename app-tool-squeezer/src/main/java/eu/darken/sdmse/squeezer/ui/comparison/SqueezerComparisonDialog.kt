package eu.darken.sdmse.squeezer.ui.comparison

import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.load
import coil.request.CachePolicy
import com.github.panpf.zoomimage.CoilZoomImageView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.copyToAutoClose
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

private val TAG = logTag("Squeezer", "Comparison", "Dialog")

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SqueezerComparisonEntryPoint {
    fun gatewaySwitch(): GatewaySwitch
}

@Composable
fun SqueezerComparisonDialog(
    media: CompressibleMedia,
    quality: Int,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val gatewaySwitch = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SqueezerComparisonEntryPoint::class.java,
        ).gatewaySwitch()
    }
    val isVideo = media is CompressibleVideo

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var originalFile by remember(media) { mutableStateOf<File?>(null) }
        var compressedFile by remember(media, quality) { mutableStateOf<File?>(null) }
        var failed by remember(media, quality) { mutableStateOf(false) }
        var tempDir by remember(media, quality) { mutableStateOf<File?>(null) }

        LaunchedEffect(media, quality) {
            failed = false
            originalFile = null
            compressedFile = null

            val unique = "preview_${System.currentTimeMillis()}_${media.identifier.value.hashCode()}"
            val dir = File(context.cacheDir, "squeezer_preview/$unique")
            tempDir = dir

            withContext(Dispatchers.IO) {
                try {
                    dir.mkdirs()

                    // Source-of-truth file the "Original" pane displays. For images this is the
                    // raw file copied from APath; for videos we extract a representative frame
                    // first and treat that JPEG as the source — the same image-comparison pipeline
                    // then re-encodes it at the selected quality on the "Compressed" side. This is
                    // approximate (single I-frame doesn't model H.264 motion artifacts) but gives
                    // users a feel for the quality slider without paying for a real partial transcode.
                    val sourceForPipeline: File = if (media is CompressibleVideo) {
                        val rawVideo = File(dir, "raw_video")
                        gatewaySwitch.file(media.path, readWrite = false).use { handle ->
                            handle.source().copyToAutoClose(rawVideo)
                        }
                        val frameFile = File(dir, "frame.jpg")
                        val frameOk = extractRepresentativeFrame(rawVideo, frameFile)
                        rawVideo.delete()
                        if (!frameOk) {
                            failed = true
                            return@withContext
                        }
                        frameFile
                    } else {
                        val cachedOriginal = File(dir, "original")
                        gatewaySwitch.file(media.path, readWrite = false).use { handle ->
                            handle.source().copyToAutoClose(cachedOriginal)
                        }
                        cachedOriginal
                    }
                    originalFile = sourceForPipeline

                    val sampled = BitmapSampler.decodeSampledBitmap(sourceForPipeline)
                    if (sampled != null) {
                        try {
                            // Videos always re-encode the sampled frame as JPEG; images keep their
                            // native format (JPEG/WebP) so the preview reflects what the actual
                            // compressor would do.
                            val format = (media as? CompressibleImage)?.compressFormat
                                ?: Bitmap.CompressFormat.JPEG
                            val baos = ByteArrayOutputStream()
                            sampled.compress(format, quality, baos)
                            val extension = when {
                                media is CompressibleImage && media.isWebp -> "webp"
                                else -> "jpg"
                            }
                            val outFile = File(dir, "compressed_q$quality.$extension")
                            FileOutputStream(outFile).use { it.write(baos.toByteArray()) }
                            compressedFile = outFile
                        } finally {
                            sampled.recycle()
                        }
                    } else {
                        failed = true
                    }
                } catch (e: Exception) {
                    log(TAG, WARN) { "Comparison preparation failed: ${e.asLog()}" }
                    failed = true
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                val toClean = tempDir
                if (toClean != null) {
                    thread(start = true, name = "squeezer-preview-cleanup") {
                        runCatching { toClean.deleteRecursively() }
                    }
                }
            }
        }

        val orientation = LocalConfiguration.current.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val originalLabel = if (isVideo) {
                    stringResource(R.string.squeezer_onboarding_video_original_label)
                } else {
                    stringResource(R.string.squeezer_onboarding_original_label)
                }
                val compressedLabel = if (isVideo) {
                    stringResource(R.string.squeezer_onboarding_video_compressed_label) + " ($quality%)"
                } else {
                    stringResource(R.string.squeezer_onboarding_compressed_label) + " ($quality%)"
                }

                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        ImagePane(
                            label = originalLabel,
                            file = originalFile,
                            failed = failed,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        ImagePane(
                            label = compressedLabel,
                            file = compressedFile,
                            failed = failed,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ImagePane(
                            label = originalLabel,
                            file = originalFile,
                            failed = failed,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        ImagePane(
                            label = compressedLabel,
                            file = compressedFile,
                            failed = failed,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.systemBars.add(WindowInsets.displayCutout))
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Close,
                        contentDescription = stringResource(CommonR.string.general_close_action),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePane(
    label: String,
    file: File?,
    failed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        when {
            file != null -> {
                AndroidView(
                    factory = { ctx -> CoilZoomImageView(ctx) },
                    update = { view ->
                        view.load(file) {
                            memoryCachePolicy(CachePolicy.DISABLED)
                            diskCachePolicy(CachePolicy.DISABLED)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            failed -> {
                Text(
                    text = stringResource(R.string.squeezer_no_savings_expected),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.systemBars.add(WindowInsets.displayCutout))
                .padding(8.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Pulls a representative frame from `videoFile` and writes it to `outFile` as JPEG. Picks a frame
 * around the 25% mark to avoid leading black/title frames. Returns true on success.
 */
private fun extractRepresentativeFrame(videoFile: File, outFile: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        val targetUs = (durationMs * 1000L * 25L / 100L).coerceAtLeast(0L)
        val bitmap = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return false
        try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            true
        } finally {
            bitmap.recycle()
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "extractRepresentativeFrame failed for ${videoFile.path}: ${e.asLog()}" }
        false
    } finally {
        runCatching { retriever.release() }
    }
}
