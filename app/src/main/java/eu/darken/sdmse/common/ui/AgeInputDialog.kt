package eu.darken.sdmse.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.databinding.ViewPreferenceInputAgeBinding
import java.time.Duration

class AgeInputDialog(
    private val activity: Activity,
    @StringRes private val titleRes: Int,
    private val minimumAge: Duration = Duration.ZERO,
    private val maximumAge: Duration = Duration.ofDays(90),
    private val currentAge: Duration,
    private val onReset: () -> Unit,
    private val onCancel: () -> Unit = {},
    private val onSave: (Duration) -> Unit,
) {

    private val context: Context
        get() = activity

    private val durationParser = DurationParser(context)

    private lateinit var dialog: AlertDialog
    private val positiveButton: Button
        get() = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

    private fun Slider.getDuration() = Duration.ofHours(value.toLong())

    private fun Slider.setDuration(duration: Duration) {
        value = duration.toHours().toFloat().coerceAtLeast(valueFrom).coerceAtMost(valueTo)
    }

    private val dialogLayout = ViewPreferenceInputAgeBinding.inflate(activity.layoutInflater, null, false).apply {
        slider.valueFrom = minimumAge.toHours().toFloat()
        slider.valueTo = maximumAge.toHours().toFloat()

        fun setSizeText(age: Duration): Unit = ageText.setText(formatAge(context, age))

        slider.setDuration(currentAge)
        setSizeText(currentAge)

        slider.setLabelFormatter { formatAge(context, slider.getDuration()) }

        ageText.addTextChangedListener { rawAge ->
            val parsedAge = durationParser.parse(rawAge.toString())
            when {
                parsedAge != null && parsedAge in minimumAge..maximumAge -> {
                    ageText.error = null
                    slider.setDuration(parsedAge)
                    positiveButton.isEnabled = true
                }

                parsedAge != null -> {
                    val minLimit = context.getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.general_age_hours,
                        minimumAge.toHours().toInt()
                    )
                    val maxLimit = context.getQuantityString2(
                        eu.darken.sdmse.common.R.plurals.general_age_days,
                        maximumAge.toDays().toInt()
                    )
                    ageText.error = "$minLimit <= X <= $maxLimit"
                    positiveButton.isEnabled = false
                }

                else -> {
                    ageText.error = context.getText(eu.darken.sdmse.common.R.string.general_error_invalid_input_label)
                    positiveButton.isEnabled = false
                }
            }
        }

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                ageText.clearFocus()
                setSizeText(slider.getDuration())
            }

            override fun onStopTrackingTouch(slider: Slider) {
                setSizeText(slider.getDuration())
            }
        })
    }

    private val dialogBuilder = MaterialAlertDialogBuilder(context).apply {
        setTitle(titleRes)
        setView(dialogLayout.root)
        setPositiveButton(eu.darken.sdmse.common.R.string.general_save_action) { _, _ ->
            onSave(dialogLayout.slider.getDuration())
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
        fun formatAge(
            context: Context,
            age: Duration
        ): String = when {
            age.toDays() > 0 -> {
                context.resources.getQuantityString(
                    eu.darken.sdmse.common.R.plurals.general_age_days,
                    age.toDays().toInt(),
                    age.toDays()
                )
            }

            else -> {
                context.resources.getQuantityString(
                    eu.darken.sdmse.common.R.plurals.general_age_hours,
                    age.toHours().toInt(),
                    age.toHours()
                )
            }
        }
    }
}