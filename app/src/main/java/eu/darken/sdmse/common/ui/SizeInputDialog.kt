package eu.darken.sdmse.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.text.format.Formatter
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import eu.darken.sdmse.databinding.ViewPreferenceInputSizeBinding

class SizeInputDialog(
    private val activity: Activity,
    @param:StringRes private val titleRes: Int,
    private val minimumSize: Long = 0,
    private val maximumSize: Long = 100 * 1000 * 1024L,
    private val currentSize: Long,
    private val onReset: () -> Unit,
    private val onCancel: () -> Unit = {},
    private val onSave: (Long) -> Unit,
) {

    private val context: Context
        get() = activity
    private lateinit var dialog: AlertDialog
    private val sizeParser = SizeParser(context)
    private val positiveButton: Button
        get() = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

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

        sizeText.addTextChangedListener { rawSize ->
            val parsedSize = sizeParser.parse(rawSize.toString())
            when {
                parsedSize != null && parsedSize in minimumSize..maximumSize -> {
                    sizeText.error = null
                    setSliderSize(parsedSize)
                    positiveButton.isEnabled = true
                }

                parsedSize != null -> {
                    val minLimit = Formatter.formatShortFileSize(context, minimumSize)
                    val maxLimit = Formatter.formatShortFileSize(context, maximumSize)
                    sizeText.error = "$minLimit <= X <= $maxLimit"
                    positiveButton.isEnabled = false
                }

                else -> {
                    sizeText.error = context.getText(eu.darken.sdmse.common.R.string.general_error_invalid_input_label)
                    positiveButton.isEnabled = false
                }
            }
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
    private val dialogBuilder = MaterialAlertDialogBuilder(context).apply {
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
        dialog = dialogBuilder.show()
    }

    companion object {
        private const val KB_MULTIPLIER = 1024L
    }
}