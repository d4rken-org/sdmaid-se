package eu.darken.sdmse.common.ui

import android.app.Activity
import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.squeezer.core.CompressionEstimator
import eu.darken.sdmse.databinding.QualityInputDialogBinding

class QualityInputDialog(
    private val activity: Activity,
    @StringRes private val titleRes: Int,
    private val currentQuality: Int,
    private val compressionEstimator: CompressionEstimator,
    private val onReset: () -> Unit,
    private val onCancel: () -> Unit = {},
    private val onSave: (Int) -> Unit,
) {

    private val context: Context
        get() = activity
    private lateinit var dialog: AlertDialog

    private fun estimateOutputRatio(quality: Int): Double {
        return compressionEstimator.estimateOutputRatio(CompressibleImage.MIME_TYPE_JPEG, quality) ?: 0.8
    }

    private val exampleInputSize = 5 * 1024 * 1024L // 5 MB

    private val dialogLayout = QualityInputDialogBinding.inflate(activity.layoutInflater, null, false).apply {
        if (BuildConfigWrap.DEBUG) qualitySlider.valueFrom = 1f
        qualitySlider.value = currentQuality.toFloat()

        fun updateUI(quality: Int) {
            qualityValue.text = "$quality%"

            val outputRatio = estimateOutputRatio(quality)
            val estimatedOutputSize = (exampleInputSize * outputRatio).toLong()
            val estimatedSavings = exampleInputSize - estimatedOutputSize
            val inputFormatted = Formatter.formatShortFileSize(context, exampleInputSize)
            val savingsFormatted = Formatter.formatShortFileSize(context, estimatedSavings)
            exampleText.text = context.getString(
                R.string.squeezer_quality_example_format,
                inputFormatted,
                savingsFormatted,
                quality
            )

            warningLowQuality.isVisible = quality <= 50
            warningHighQuality.isVisible = quality > 95
        }

        updateUI(currentQuality)

        qualitySlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                updateUI(slider.value.toInt())
            }
        })

        qualitySlider.addOnChangeListener { _, value, _ ->
            updateUI(value.toInt())
        }
    }

    private val dialogBuilder = MaterialAlertDialogBuilder(context).apply {
        setTitle(titleRes)
        setView(dialogLayout.root)
        setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
            onSave(dialogLayout.qualitySlider.value.toInt())
        }
        setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
            onCancel()
        }
        setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
            onReset()
        }
    }

    fun show() {
        dialog = dialogBuilder.show()
    }
}
