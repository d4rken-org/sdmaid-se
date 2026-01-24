package eu.darken.sdmse.compressor.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.compressor.core.CompressibleImage
import eu.darken.sdmse.compressor.ui.onboarding.CompressorOnboardingDialog
import eu.darken.sdmse.databinding.CompressorPreviewDialogBinding
import javax.inject.Inject


class PreviewCompressionDialog @Inject constructor(
    private val fragment: Fragment,
    private val onboardingDialog: CompressorOnboardingDialog,
) {

    fun show(
        items: List<CompressibleImage>,
        quality: Int,
        onPositive: (quality: Int) -> Unit,
        onNegative: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val layoutInflater = LayoutInflater.from(context)

        val binding = CompressorPreviewDialogBinding.inflate(layoutInflater)

        val totalSize = items.sumOf { it.size }
        val estimatedSavings = items.sumOf { it.estimatedSavings ?: 0L }

        binding.apply {
            itemCount.text = context.resources.getQuantityString(
                R.plurals.compressor_preview_x_images,
                items.size,
                items.size
            )

            totalSizeValue.text = Formatter.formatShortFileSize(context, totalSize)
            estimatedSavingsValue.text = Formatter.formatShortFileSize(context, estimatedSavings)
        }

        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.compressor_preview_dialog_title)
            setView(binding.root)
            setPositiveButton(R.string.compressor_compress_action) { _, _ ->
                onPositive(quality)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                onNegative()
            }
            setNeutralButton(R.string.compressor_preview_info_action) { _, _ ->
                onboardingDialog.show(
                    sampleImage = items.first(),
                    quality = quality,
                    onDismiss = {
                        // Re-show this dialog after details are dismissed
                        show(items, quality, onPositive, onNegative)
                    },
                )
            }
        }.show()
    }
}
