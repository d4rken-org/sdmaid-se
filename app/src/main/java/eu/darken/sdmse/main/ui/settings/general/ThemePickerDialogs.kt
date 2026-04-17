package eu.darken.sdmse.main.ui.settings.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.device.RomType
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeStyle
import eu.darken.sdmse.common.R as CommonR

@Composable
fun ThemeModePickerDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_theme_mode_setting_label)) },
        text = {
            val options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.ui_theme_mode_system_label),
                ThemeMode.DARK to stringResource(R.string.ui_theme_mode_dark_label),
                ThemeMode.LIGHT to stringResource(R.string.ui_theme_mode_light_label),
            )
            androidx.compose.foundation.layout.Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) },
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
fun ThemeStylePickerDialog(
    currentStyle: ThemeStyle,
    onStyleSelected: (ThemeStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_theme_style_setting_label)) },
        text = {
            val options = listOf(
                ThemeStyle.DEFAULT to stringResource(R.string.ui_theme_style_default_label),
                ThemeStyle.MATERIAL_YOU to stringResource(R.string.ui_theme_style_materialyou_label),
                ThemeStyle.MEDIUM_CONTRAST to stringResource(R.string.ui_theme_style_mediumcontrast_label),
                ThemeStyle.HIGH_CONTRAST to stringResource(R.string.ui_theme_style_highcontrast_label),
            )
            androidx.compose.foundation.layout.Column {
                options.forEach { (style, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(style) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = style == currentStyle,
                            onClick = { onStyleSelected(style) },
                        )
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}

@Composable
fun RomTypePickerDialog(
    currentRomType: RomType,
    onRomTypeSelected: (RomType) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(eu.darken.sdmse.appcleaner.R.string.appcleaner_automation_romtype_detection_label)) },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                RomType.entries.forEach { rom ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRomTypeSelected(rom)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = rom == currentRomType,
                            onClick = {
                                onRomTypeSelected(rom)
                                onDismiss()
                            },
                        )
                        Text(
                            text = rom.label.get(context),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}
