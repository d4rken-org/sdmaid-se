package eu.darken.sdmse.squeezer.ui.onboarding

import android.app.Dialog
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.databinding.SqueezerOnboardingDialogBinding
import eu.darken.sdmse.squeezer.core.CompressibleImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
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

        // Display sizes immediately (no bitmap work needed)
        val originalSize = sampleImage.size
        val compressedSize = sampleImage.estimatedCompressedSize ?: originalSize
        binding.originalSize.text = Formatter.formatShortFileSize(context, originalSize)
        binding.compressedSize.text = "~${Formatter.formatShortFileSize(context, compressedSize)}"

        binding.qualityIndicator.text = context.getString(
            R.string.squeezer_onboarding_quality_label,
            quality,
        )

        // Load and compress thumbnails off the main thread
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val sampledBitmap = BitmapSampler.decodeSampledBitmap(imageFile, maxDimension = 512)

            if (sampledBitmap != null) {
                val format = sampleImage.compressFormat
                val baos = ByteArrayOutputStream()
                sampledBitmap.compress(format, quality, baos)
                val bytes = baos.toByteArray()
                val compressedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                withContext(Dispatchers.Main) {
                    binding.originalImage.setImageBitmap(sampledBitmap)
                    binding.compressedImage.setImageBitmap(compressedBitmap)
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.originalImage.setImageResource(R.drawable.splash_mascot)
                    binding.compressedImage.setImageResource(R.drawable.splash_mascot)
                    binding.compressedSize.text = context.getString(R.string.squeezer_no_savings_expected)
                }
            }
        }

        val dialog: Dialog = MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.squeezer_onboarding_title)
            setView(binding.root)
            setPositiveButton(R.string.squeezer_onboarding_got_it) { _, _ ->
                onDismiss()
            }
            setNeutralButton(R.string.squeezer_compare_action) { _, _ ->
                ComparisonDialog.newInstance(path, quality, sampleImage.isWebp)
                    .show(fragment.childFragmentManager, "comparison")
                onDismiss()
            }
            setOnCancelListener {
                onDismiss()
            }
            setOnDismissListener {
                // Recycle bitmaps when dialog is dismissed
                (binding.originalImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
                (binding.compressedImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            }
        }.show()

        val openComparison = View.OnClickListener {
            dialog.dismiss()
            ComparisonDialog.newInstance(path, quality, sampleImage.isWebp)
                .show(fragment.childFragmentManager, "comparison")
            onDismiss()
        }
        binding.originalImage.setOnClickListener(openComparison)
        binding.compressedImage.setOnClickListener(openComparison)
    }
}
