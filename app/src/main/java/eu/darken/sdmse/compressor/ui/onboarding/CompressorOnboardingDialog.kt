package eu.darken.sdmse.compressor.ui.onboarding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.compressor.core.CompressibleImage
import eu.darken.sdmse.databinding.CompressorOnboardingDialogBinding
import java.io.ByteArrayOutputStream
import javax.inject.Inject


class CompressorOnboardingDialog @Inject constructor(
    private val fragment: Fragment,
) {

    fun show(
        sampleImage: CompressibleImage,
        quality: Int,
        onDismiss: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val layoutInflater = LayoutInflater.from(context)
        val binding = CompressorOnboardingDialogBinding.inflate(layoutInflater)

        val path = (sampleImage.path as? LocalPath)?.path ?: sampleImage.path.path

        // Load original image at full resolution for accurate preview comparison
        val originalBitmap = BitmapFactory.decodeFile(path)

        var compressedBitmap: Bitmap? = null

        if (originalBitmap != null) {
            // Show original image
            binding.originalImage.setImageBitmap(originalBitmap)

            // Compress to memory
            val outputStream = ByteArrayOutputStream()
            val format = if (sampleImage.isWebp) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }
            originalBitmap.compress(format, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Decode compressed bytes
            compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
            binding.compressedImage.setImageBitmap(compressedBitmap)

            // Display sizes - use estimated size from the actual file, not the preview
            val originalSize = sampleImage.size
            val compressedSize = sampleImage.estimatedCompressedSize ?: originalSize

            binding.originalSize.text = Formatter.formatShortFileSize(context, originalSize)
            binding.compressedSize.text = "~${Formatter.formatShortFileSize(context, compressedSize)}"

            // Set up click listeners for zoomable preview
            val originalLabel = context.getString(R.string.compressor_onboarding_original_label)
            val compressedLabel = context.getString(R.string.compressor_onboarding_compressed_label)

            binding.originalImage.setOnClickListener {
                ZoomablePreviewDialog.newInstance(path, originalLabel)
                    .show(fragment.childFragmentManager, "zoomable_original")
            }

            binding.compressedImage.setOnClickListener {
                compressedBitmap?.let { bitmap ->
                    ZoomablePreviewDialog.newInstance(bitmap, compressedLabel)
                        .show(fragment.childFragmentManager, "zoomable_compressed")
                }
            }
        } else {
            // Fallback: just show placeholders
            binding.originalImage.setImageResource(R.drawable.splash_mascot)
            binding.compressedImage.setImageResource(R.drawable.splash_mascot)
            binding.originalSize.text = Formatter.formatShortFileSize(context, sampleImage.size)
            binding.compressedSize.text = context.getString(R.string.compressor_no_savings_expected)
        }

        binding.qualityIndicator.text = context.getString(
            R.string.compressor_onboarding_quality_label,
            quality,
        )

        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.compressor_onboarding_title)
            setView(binding.root)
            setPositiveButton(R.string.compressor_onboarding_got_it) { _, _ ->
                onDismiss()
            }
            setOnCancelListener {
                onDismiss()
            }
        }.show()
    }
}
