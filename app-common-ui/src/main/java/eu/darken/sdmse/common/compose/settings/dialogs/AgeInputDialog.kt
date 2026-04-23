package eu.darken.sdmse.common.compose.settings.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.getQuantityString2
import eu.darken.sdmse.common.ui.DurationParser
import eu.darken.sdmse.common.ui.formatAge
import java.time.Duration

@Composable
fun AgeInputDialog(
    @StringRes titleRes: Int,
    currentAge: Duration,
    onSave: (Duration) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    minimumAge: Duration = Duration.ZERO,
    maximumAge: Duration = Duration.ofDays(90),
) {
    val context = LocalContext.current
    val durationParser = remember(context) { DurationParser(context) }

    val minHours = minimumAge.toHours()
    val maxHours = maximumAge.toHours()

    val initialValue = when {
        currentAge < minimumAge -> minimumAge
        currentAge > maximumAge -> maximumAge
        else -> currentAge
    }

    var sliderHours by remember { mutableStateOf(initialValue.toHours().toFloat()) }
    var textValue by remember { mutableStateOf(formatAge(context, initialValue)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        textValue = raw
                        val parsed = durationParser.parse(raw)
                        when {
                            parsed != null && parsed in minimumAge..maximumAge -> {
                                error = null
                                sliderHours = parsed.toHours().toFloat()
                                saveEnabled = true
                            }
                            parsed != null -> {
                                val minLabel = context.getQuantityString2(
                                    R.plurals.general_age_hours,
                                    minimumAge.toHours().toInt(),
                                )
                                val maxLabel = context.getQuantityString2(
                                    R.plurals.general_age_days,
                                    maximumAge.toDays().toInt(),
                                )
                                error = "$minLabel <= X <= $maxLabel"
                                saveEnabled = false
                            }
                            else -> {
                                error = context.getString(R.string.general_error_invalid_input_label)
                                saveEnabled = false
                            }
                        }
                    },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderHours.coerceIn(minHours.toFloat(), maxHours.toFloat()),
                    onValueChange = { value ->
                        sliderHours = value
                        val duration = Duration.ofHours(value.toLong())
                        textValue = formatAge(context, duration)
                        error = null
                        saveEnabled = true
                    },
                    valueRange = minHours.toFloat()..maxHours.toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = saveEnabled,
                onClick = { onSave(Duration.ofHours(sliderHours.toLong())) },
            ) { Text(stringResource(R.string.general_save_action)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.general_reset_action))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.general_cancel_action))
                }
            }
        },
    )
}

@Preview2
@Composable
private fun AgeInputDialogPreview() {
    PreviewWrapper {
        AgeInputDialog(
            titleRes = R.string.general_save_action,
            currentAge = Duration.ofDays(7),
            onSave = {},
            onReset = {},
            onDismiss = {},
        )
    }
}
