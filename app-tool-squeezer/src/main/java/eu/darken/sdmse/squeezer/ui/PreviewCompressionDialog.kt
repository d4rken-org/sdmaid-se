package eu.darken.sdmse.squeezer.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.squeezer.databinding.SqueezerPreviewDialogBinding
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.core.CompressibleVideo
import eu.darken.sdmse.squeezer.ui.onboarding.ComparisonDialog
import javax.inject.Inject


class PreviewCompressionDialog @Inject constructor(
    private val fragment: Fragment,
) {

    fun show(
        items: List<CompressibleMedia>,
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
                eu.darken.sdmse.squeezer.R.plurals.squeezer_preview_x_images,
                items.size,
                items.size
            )

            totalSizeValue.text = Formatter.formatShortFileSize(context, totalSize)
            estimatedSavingsValue.text = Formatter.formatShortFileSize(context, estimatedSavings)
        }

        MaterialAlertDialogBuilder(context).apply {
            setTitle(eu.darken.sdmse.squeezer.R.string.squeezer_preview_dialog_title)
            setView(binding.root)
            setPositiveButton(eu.darken.sdmse.squeezer.R.string.squeezer_compress_action) { _, _ ->
                onPositive(quality)
            }
            setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
                onNegative()
            }

            val hasOnlyImages = items.all { it is CompressibleImage }
            val hasOnlyVideos = items.all { it is CompressibleVideo }
            if (hasOnlyImages || hasOnlyVideos) {
                setNeutralButton(eu.darken.sdmse.squeezer.R.string.squeezer_compare_action) { _, _ ->
                    val sample = items.firstOrNull() ?: return@setNeutralButton
                    val path = (sample.path as? LocalPath)?.path ?: return@setNeutralButton

                    fragment.childFragmentManager.setFragmentResultListener(
                        ComparisonDialog.REQUEST_KEY,
                        fragment.viewLifecycleOwner,
                    ) { _, _ ->
                        show(items, quality, onPositive, onNegative)
                    }
                    val isWebp = (sample as? CompressibleImage)?.isWebp == true
                    val isVideoSource = sample is CompressibleVideo
                    ComparisonDialog.newInstance(path, quality, isWebp, isVideoSource)
                        .show(fragment.childFragmentManager, "comparison")
                }
            }
        }.show()
    }
}
