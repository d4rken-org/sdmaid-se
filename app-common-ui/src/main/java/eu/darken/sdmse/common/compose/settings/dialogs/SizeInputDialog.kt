package eu.darken.sdmse.common.compose.settings.dialogs

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import eu.darken.sdmse.common.compose.dialog.SdmAlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.dialog.SdmDialogButtonBar
import eu.darken.sdmse.common.compose.focus.dpadVerticalFieldEscape
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.SizeParser

private const val KB_MULTIPLIER = 1024L

@Composable
fun SizeInputDialog(
    @StringRes titleRes: Int,
    currentSize: Long,
    onSave: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    minimumSize: Long = 0,
    maximumSize: Long = 100L * 1000L * 1024L,
    @StringRes resetLabelRes: Int = R.string.general_reset_action,
) {
    val context = LocalContext.current
    val sizeParser = remember(context) { SizeParser(context) }

    // An inverted range would throw out of coerceIn() and out of the slider's valueRange. Every
    // current caller passes max > min, so this only guards future ones.
    val effectiveMax = maxOf(maximumSize, minimumSize)

    val minKb = minimumSize / KB_MULTIPLIER
    val maxKb = effectiveMax / KB_MULTIPLIER

    val initialValue = currentSize.coerceIn(minimumSize, effectiveMax)

    var sliderKb by remember { mutableStateOf(initialValue.toFloat() / KB_MULTIPLIER) }
    var textValue by remember { mutableStateOf(Formatter.formatShortFileSize(context, initialValue)) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveEnabled by remember { mutableStateOf(true) }

    SdmAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { raw ->
                        textValue = raw
                        val parsed = sizeParser.parse(raw)
                        when {
                            parsed != null && parsed in minimumSize..effectiveMax -> {
                                error = null
                                sliderKb = parsed.toFloat() / KB_MULTIPLIER
                                saveEnabled = true
                            }
                            parsed != null -> {
                                val minLabel = Formatter.formatShortFileSize(context, minimumSize)
                                val maxLabel = Formatter.formatShortFileSize(context, effectiveMax)
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
                    // Without this the field swallows D-pad UP/DOWN and a TV remote can never
                    // reach the buttons below.
                    modifier = Modifier
                        .fillMaxWidth()
                        .dpadVerticalFieldEscape(),
                )
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = sliderKb.coerceIn(minKb.toFloat(), maxKb.toFloat()),
                    onValueChange = { value ->
                        sliderKb = value
                        val bytes = value.toLong() * KB_MULTIPLIER
                        textValue = Formatter.formatShortFileSize(context, bytes)
                        error = null
                        saveEnabled = true
                    },
                    valueRange = minKb.toFloat()..maxKb.toFloat(),
                )
            }
        },
        confirmButton = {
            SdmDialogButtonBar(
                // The text field owns the focus here; a button claiming it swallows the typing.
                autoFocus = false,
                positive = SdmDialogAction(
                    label = stringResource(R.string.general_save_action),
                    enabled = saveEnabled,
                    onClick = { onSave(sliderKb.toLong() * KB_MULTIPLIER) },
                ),
                negative = SdmDialogAction(
                    label = stringResource(R.string.general_cancel_action),
                    onClick = onDismiss,
                ),
                neutral = SdmDialogAction(
                    label = stringResource(resetLabelRes),
                    onClick = onReset,
                ),
            )
        },
    )
}

@Preview2
@Composable
private fun SizeInputDialogPreview() {
    PreviewWrapper {
        SizeInputDialog(
            titleRes = R.string.general_save_action,
            currentSize = 16 * 1024L,
            onSave = {},
            onReset = {},
            onDismiss = {},
        )
    }
}
