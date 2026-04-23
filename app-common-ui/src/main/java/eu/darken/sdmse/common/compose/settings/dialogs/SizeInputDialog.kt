package eu.darken.sdmse.common.compose.settings.dialogs

import android.text.format.Formatter
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
) {
    val context = LocalContext.current
    val sizeParser = remember(context) { SizeParser(context) }

    val minKb = minimumSize / KB_MULTIPLIER
    val maxKb = maximumSize / KB_MULTIPLIER

    val initialValue = currentSize.coerceIn(minimumSize, maximumSize)

    var sliderKb by remember { mutableStateOf(initialValue.toFloat() / KB_MULTIPLIER) }
    var textValue by remember { mutableStateOf(Formatter.formatShortFileSize(context, initialValue)) }
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
                        val parsed = sizeParser.parse(raw)
                        when {
                            parsed != null && parsed in minimumSize..maximumSize -> {
                                error = null
                                sliderKb = parsed.toFloat() / KB_MULTIPLIER
                                saveEnabled = true
                            }
                            parsed != null -> {
                                val minLabel = Formatter.formatShortFileSize(context, minimumSize)
                                val maxLabel = Formatter.formatShortFileSize(context, maximumSize)
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
                    value = sliderKb.coerceIn(minKb.toFloat(), maxKb.toFloat()),
                    onValueChange = { value ->
                        sliderKb = value
                        val bytes = value.toLong() * KB_MULTIPLIER
                        textValue = Formatter.formatShortFileSize(context, bytes)
                        error = null
                        saveEnabled = true
                    },
                    valueRange = minKb.toFloat()..maxKb.toFloat(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = saveEnabled,
                onClick = {
                    onSave(sliderKb.toLong() * KB_MULTIPLIER)
                },
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
