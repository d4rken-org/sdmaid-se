package eu.darken.sdmse.common.ui

import android.app.Activity
import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import eu.darken.sdmse.databinding.ViewPreferenceInputSizeBinding

class SizeInputDialog(
    private val activity: Activity,
    @StringRes private val titleRes: Int,
    private val minimumSize: Long = 0,
    private val maximumSize: Long = 100 * 1000 * 1024L,
    private val currentSize: Long,
    private val onReset: () -> Unit,
    private val onCancel: () -> Unit = {},
    private val onSave: (Long) -> Unit,
) {

    private val context: Context
        get() = activity

    private fun parseSize(input: String): Long? {
        val match = SIZE_UNITS_REGEX.matchEntire(input.trim()) ?: return null
        val (value, _, unit) = match.destructured
        val factor = SIZE_UNITS[unit.uppercase()] ?: return null
        return (value.toDoubleOrNull()?.times(factor))?.toLong()
    }

    private val dialogLayout = ViewPreferenceInputSizeBinding.inflate(activity.layoutInflater, null, false).apply {
        slider.valueFrom = minimumSize.toFloat()
        slider.valueTo = (maximumSize / KB_MULTIPLIER).toFloat()

        fun setSliderSize(size: Long) {
            slider.value = (size.coerceAtMost(maximumSize) / KB_MULTIPLIER)
                .coerceAtLeast(minimumSize / KB_MULTIPLIER)
                .toFloat()
        }

        fun getSliderSize(): Long = slider.value.toLong() * KB_MULTIPLIER
        fun setSizeText(size: Long): Unit = sizeText.setText(Formatter.formatShortFileSize(context, size))

        setSliderSize(currentSize)
        setSizeText(currentSize)

        slider.setLabelFormatter { Formatter.formatShortFileSize(context, getSliderSize()) }

        sizeText.addTextChangedListener {
            parseSize(it.toString())?.let { setSliderSize(it) }
        }

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                sizeText.clearFocus()
                setSizeText(getSliderSize())
            }

            override fun onStopTrackingTouch(slider: Slider) {
                setSizeText(getSliderSize())
            }
        })
    }
    private val dialog = MaterialAlertDialogBuilder(context).apply {
        setTitle(titleRes)
        setView(dialogLayout.root)
        setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
            onSave(dialogLayout.slider.value.toLong() * KB_MULTIPLIER)
        }
        setNegativeButton(eu.darken.sdmse.common.R.string.general_cancel_action) { _, _ ->
            onCancel()
        }
        setNeutralButton(eu.darken.sdmse.common.R.string.general_reset_action) { _, _ ->
            onReset()
        }
    }

    fun show() {
        dialog.show()
    }

    companion object {
        private const val KB_MULTIPLIER = 1024L
        private val SIZE_UNITS_REGEX = Regex("(\\d+(\\.\\d+)?)\\s*(B|KB|MB|GB)", RegexOption.IGNORE_CASE)
        private val SIZE_UNITS = mapOf(
            "B" to 1L,
            "KB" to 1_000L,
            "MB" to 1_000_000L,
            "GB" to 1_000_000_000L,
        )
    }
}