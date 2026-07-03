package eu.darken.sdmse.squeezer.ui.comparison

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
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
import eu.darken.sdmse.squeezer.core.processor.HeifWriterEncoder
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
    val heifWriterEncoder = remember { HeifWriterEncoder() }
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
        // Track EVERY dir created during this dialog's lifetime: tempDir is re-keyed on quality, so
        // a single tempDir snapshot at dispose only cleans the last one and orphans the rest.
        val createdDirs = remember { mutableListOf<File>() }

        // The two zoom views, captured so their gestures can be linked (see linkZoomGestures).
        var originalView by remember { mutableStateOf<CoilZoomImageView?>(null) }
        var compressedView by remember { mutableStateOf<CoilZoomImageView?>(null) }

        LaunchedEffect(media, quality) {
            failed = false
            originalFile = null
            compressedFile = null

            val unique = "preview_${System.currentTimeMillis()}_${media.identifier.value.hashCode()}"
            val dir = File(context.cacheDir, "squeezer_preview/$unique")
            createdDirs.add(dir)

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
                            // HEIC has no Bitmap.CompressFormat and can't be encoded into an
                            // in-memory stream — route it through HeifWriter (writes to a file)
                            // so the preview reflects a real HEIF re-encode. JPEG/WebP keep their
                            // native format; videos re-encode the sampled frame as JPEG.
                            val outFile = if (media is CompressibleImage && media.isHeic) {
                                val heic = File(dir, "compressed_q$quality.heic")
                                heifWriterEncoder.encode(sampled, media.mimeType, quality, heic, null)
                                heic
                            } else {
                                val format = (media as? CompressibleImage)?.compressFormat
                                    ?: Bitmap.CompressFormat.JPEG
                                val baos = ByteArrayOutputStream()
                                sampled.compress(format, quality, baos)
                                val extension = when {
                                    media is CompressibleImage && media.isWebp -> "webp"
                                    else -> "jpg"
                                }
                                val jpegOrWebp = File(dir, "compressed_q$quality.$extension")
                                FileOutputStream(jpegOrWebp).use { it.write(baos.toByteArray()) }
                                jpegOrWebp
                            }
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
                val toClean = createdDirs.toList()
                if (toClean.isNotEmpty()) {
                    thread(start = true, name = "squeezer-preview-cleanup") {
                        toClean.forEach { runCatching { it.deleteRecursively() } }
                    }
                }
            }
        }

        // Link zoom/pan across both panes once both views exist, so pinching or panning one
        // mirrors onto the other and the user can compare the same detail at the same zoom level.
        val original = originalView
        val compressed = compressedView
        DisposableEffect(original, compressed) {
            if (original != null && compressed != null) {
                linkZoomGestures(original, compressed)
            }
            onDispose {
                original?.setOnTouchListener(null)
                compressed?.setOnTouchListener(null)
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
                    stringResource(R.string.squeezer_onboarding_video_compressed_label) +
                        " ($quality%)\n" +
                        stringResource(R.string.squeezer_onboarding_video_quality_disclaimer)
                } else {
                    stringResource(R.string.squeezer_onboarding_compressed_label) + " ($quality%)"
                }

                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        ImagePane(
                            label = originalLabel,
                            file = originalFile,
                            failed = failed,
                            labelAlignment = Alignment.BottomStart,
                            onViewReady = { originalView = it },
                            onViewReleased = { if (originalView === it) originalView = null },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        PaneDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                        )
                        ImagePane(
                            label = compressedLabel,
                            file = compressedFile,
                            failed = failed,
                            labelAlignment = Alignment.BottomEnd,
                            onViewReady = { compressedView = it },
                            onViewReleased = { if (compressedView === it) compressedView = null },
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
                            labelAlignment = Alignment.TopCenter,
                            onViewReady = { originalView = it },
                            onViewReleased = { if (originalView === it) originalView = null },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        PaneDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp),
                        )
                        ImagePane(
                            label = compressedLabel,
                            file = compressedFile,
                            failed = failed,
                            labelAlignment = Alignment.BottomCenter,
                            onViewReady = { compressedView = it },
                            onViewReleased = { if (compressedView === it) compressedView = null },
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
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
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
    labelAlignment: Alignment = Alignment.TopStart,
    onViewReady: (CoilZoomImageView) -> Unit = {},
    onViewReleased: (CoilZoomImageView) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        // Clip the zoom view to the pane: when zoomed in, its enlarged content would otherwise
        // draw past its bounds and overlap the adjacent pane (Compose's AndroidView host doesn't
        // clip the embedded view's overflow).
        modifier = modifier
            .clipToBounds()
            .background(Color.Black),
    ) {
        when {
            file != null -> {
                AndroidView(
                    factory = { ctx -> CoilZoomImageView(ctx).also(onViewReady) },
                    update = { view ->
                        view.load(file) {
                            memoryCachePolicy(CachePolicy.DISABLED)
                            diskCachePolicy(CachePolicy.DISABLED)
                        }
                    },
                    onRelease = onViewReleased,
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
                .align(labelAlignment)
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(8.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PaneDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.White.copy(alpha = 0.3f)))
}

/**
 * Mirrors zoom/pan gestures between the two comparison panes by replaying each view's touch events
 * onto the other, so zooming into a detail on one side shows the same detail on the other. Both
 * panes are always the same size, so identical view-local coordinates land on the same content
 * point. Forwarded events go straight to [android.view.View.onTouchEvent] (bypassing the listener),
 * so there is no feedback loop; returning false lets the originating view still handle the gesture.
 */
@SuppressLint("ClickableViewAccessibility")
private fun linkZoomGestures(viewA: CoilZoomImageView, viewB: CoilZoomImageView) {
    viewA.setOnTouchListener { _, event ->
        val cloned = MotionEvent.obtain(event)
        try {
            viewB.onTouchEvent(cloned)
        } finally {
            cloned.recycle()
        }
        false
    }
    viewB.setOnTouchListener { _, event ->
        val cloned = MotionEvent.obtain(event)
        try {
            viewA.onTouchEvent(cloned)
        } finally {
            cloned.recycle()
        }
        false
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
