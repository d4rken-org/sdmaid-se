package eu.darken.sdmse.squeezer.ui.onboarding

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.copyToAutoClose
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.databinding.SqueezerOnboardingDialogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject


class SqueezerOnboardingDialog @Inject constructor(
    private val fragment: Fragment,
    private val gatewaySwitch: GatewaySwitch,
) {

    fun show(
        sampleImage: CompressibleImage,
        quality: Int,
        onDismiss: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val layoutInflater = LayoutInflater.from(context)
        val binding = SqueezerOnboardingDialogBinding.inflate(layoutInflater)

        // Display sizes immediately (no bitmap work needed)
        val originalSize = sampleImage.size
        val compressedSize = sampleImage.estimatedCompressedSize ?: originalSize
        binding.originalSize.text = Formatter.formatShortFileSize(context, originalSize)
        binding.compressedSize.text = "~${Formatter.formatShortFileSize(context, compressedSize)}"

        binding.qualityIndicator.text = context.getString(
            R.string.squeezer_onboarding_quality_label,
            quality,
        )

        val dialog = MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.squeezer_onboarding_title)
            setView(binding.root)
            setPositiveButton(eu.darken.sdmse.common.R.string.general_gotit_action) { _, _ ->
                onDismiss()
            }
            // Listener set after cache copy succeeds
            setNeutralButton(R.string.squeezer_compare_action, null)
            setOnCancelListener {
                onDismiss()
            }
            setOnDismissListener {
                // Recycle bitmaps when dialog is dismissed
                (binding.originalImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
                (binding.compressedImage.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            }
        }.show() as AlertDialog

        // Disable compare until the image is cached locally via gateway
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false

        // Cache image via gateway and load thumbnails off the main thread
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val previewDir = File(context.cacheDir, "squeezer_preview")
            previewDir.mkdirs()
            val cachedFile = File(previewDir, "original")

            try {
                gatewaySwitch.file(sampleImage.path, readWrite = false).use { handle ->
                    handle.source().copyToAutoClose(cachedFile)
                }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to cache image via gateway: ${e.asLog()}" }
                withContext(Dispatchers.Main) {
                    binding.originalImage.setImageResource(R.drawable.splash_mascot)
                    binding.compressedImage.setImageResource(R.drawable.splash_mascot)
                    binding.compressedSize.text = context.getString(R.string.squeezer_no_savings_expected)
                }
                return@launch
            }

            val sampledBitmap = BitmapSampler.decodeSampledBitmap(cachedFile, maxDimension = 512)

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

            // Enable compare now that the cached file is ready
            withContext(Dispatchers.Main) {
                val cachedPath = cachedFile.absolutePath
                val openComparison = {
                    dialog.dismiss()
                    ComparisonDialog.newInstance(cachedPath, quality, sampleImage.isWebp)
                        .show(fragment.childFragmentManager, "comparison")
                    onDismiss()
                }

                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                    isEnabled = true
                    setOnClickListener { openComparison() }
                }
                binding.originalImage.setOnClickListener { openComparison() }
                binding.compressedImage.setOnClickListener { openComparison() }
            }
        }
    }

    companion object {
        private val TAG = logTag("Squeezer", "OnboardingDialog")
    }
}
