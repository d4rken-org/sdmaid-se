package eu.darken.sdmse.squeezer.ui.onboarding

import android.graphics.Bitmap
import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.databinding.SqueezerOnboardingDialogBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


class SqueezerOnboardingDialog @Inject constructor(
    private val fragment: Fragment,
) {

    fun show(
        sampleImage: CompressibleImage,
        quality: Int,
        onDismiss: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val layoutInflater = LayoutInflater.from(context)
        val binding = SqueezerOnboardingDialogBinding.inflate(layoutInflater)

        val path = (sampleImage.path as? LocalPath)?.path ?: sampleImage.path.path
        val imageFile = File(path)

        // Clean up old temp files
        val previewDir = File(context.cacheDir, "squeezer_preview")
        previewDir.deleteRecursively()

        // Load sampled bitmap for thumbnails (avoids Canvas crash on large images)
        val sampledBitmap = BitmapSampler.decodeSampledBitmap(imageFile)

        if (sampledBitmap != null) {
            // Show original image thumbnail
            binding.originalImage.setImageBitmap(sampledBitmap)

            // Compress the sampled bitmap
            val outputStream = ByteArrayOutputStream()
            val format = if (sampleImage.isWebp) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }
            sampledBitmap.compress(format, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Write compressed bytes to temp file for zoom preview
            previewDir.mkdirs()
            val extension = if (sampleImage.isWebp) "webp" else "jpg"
            val compressedFile = File(previewDir, "compressed_preview.$extension")
            FileOutputStream(compressedFile).use { it.write(compressedBytes) }

            // Show compressed thumbnail
            val compressedBitmap = BitmapSampler.decodeSampledBitmap(compressedFile)
            binding.compressedImage.setImageBitmap(compressedBitmap)

            // Display sizes - use estimated size from the actual file, not the preview
            val originalSize = sampleImage.size
            val compressedSize = sampleImage.estimatedCompressedSize ?: originalSize

            binding.originalSize.text = Formatter.formatShortFileSize(context, originalSize)
            binding.compressedSize.text = "~${Formatter.formatShortFileSize(context, compressedSize)}"

            // Set up click listeners for zoomable preview
            val originalLabel = context.getString(R.string.squeezer_onboarding_original_label)
            val compressedLabel = context.getString(R.string.squeezer_onboarding_compressed_label)

            binding.originalImage.setOnClickListener {
                ZoomablePreviewDialog.newInstance(path, originalLabel)
                    .show(fragment.childFragmentManager, "zoomable_original")
            }

            binding.compressedImage.setOnClickListener {
                ZoomablePreviewDialog.newInstance(compressedFile.absolutePath, compressedLabel)
                    .show(fragment.childFragmentManager, "zoomable_compressed")
            }
        } else {
            // Fallback: just show placeholders
            binding.originalImage.setImageResource(R.drawable.splash_mascot)
            binding.compressedImage.setImageResource(R.drawable.splash_mascot)
            binding.originalSize.text = Formatter.formatShortFileSize(context, sampleImage.size)
            binding.compressedSize.text = context.getString(R.string.squeezer_no_savings_expected)
        }

        binding.qualityIndicator.text = context.getString(
            R.string.squeezer_onboarding_quality_label,
            quality,
        )

        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.squeezer_onboarding_title)
            setView(binding.root)
            setPositiveButton(R.string.squeezer_onboarding_got_it) { _, _ ->
                onDismiss()
            }
            setOnCancelListener {
                onDismiss()
            }
        }.show()
    }
}
