package eu.darken.sdmse.squeezer.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.databinding.SqueezerPreviewDialogBinding
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.ui.onboarding.ComparisonDialog
import javax.inject.Inject


class PreviewCompressionDialog @Inject constructor(
    private val fragment: Fragment,
) {

    fun show(
        items: List<CompressibleImage>,
        quality: Int,
        onPositive: (quality: Int) -> Unit,
        onNegative: () -> Unit,
    ) {
        val context = fragment.requireContext()
        val layoutInflater = LayoutInflater.from(context)

        val binding = SqueezerPreviewDialogBinding.inflate(layoutInflater)

        val totalSize = items.sumOf { it.size }
        val estimatedSavings = items.sumOf { it.estimatedSavings ?: 0L }

        binding.apply {
            itemCount.text = context.resources.getQuantityString(
                R.plurals.squeezer_preview_x_images,
                items.size,
                items.size
            )

            totalSizeValue.text = Formatter.formatShortFileSize(context, totalSize)
            estimatedSavingsValue.text = Formatter.formatShortFileSize(context, estimatedSavings)
        }

        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.squeezer_preview_dialog_title)
            setView(binding.root)
            setPositiveButton(R.string.squeezer_compress_action) { _, _ ->
                onPositive(quality)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                onNegative()
            }
            setNeutralButton(R.string.squeezer_compare_action) { _, _ ->
                val sampleImage = items.firstOrNull() ?: return@setNeutralButton
                val path = (sampleImage.path as? LocalPath)?.path ?: sampleImage.path.path

                fragment.childFragmentManager.setFragmentResultListener(
                    ComparisonDialog.REQUEST_KEY,
                    fragment.viewLifecycleOwner,
                ) { _, _ ->
                    show(items, quality, onPositive, onNegative)
                }
                ComparisonDialog.newInstance(path, quality, sampleImage.isWebp)
                    .show(fragment.childFragmentManager, "comparison")
            }
        }.show()
    }
}
