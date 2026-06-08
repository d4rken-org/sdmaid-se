package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SdmModalBottomSheet
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Shield
import eu.darken.sdmse.common.compose.icons.Shizuku
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.DebugCardProvider

/**
 * Which diagnostic result is currently shown in the bottom sheet. A single nullable value of this
 * type drives the sheet so the two test results can never fight over the same surface.
 */
internal sealed interface DebugTestSheet {
    data class Root(val result: DebugCardProvider.RootTestResult) : DebugTestSheet
    data class Shizuku(val result: DebugCardProvider.ShizukuTestResult) : DebugTestSheet
}

/** Field label + tri-state value (true / false / unknown). */
internal data class DebugStatus(val label: String, val state: Boolean?)

internal fun DebugTestSheet.statuses(): List<DebugStatus> = when (this) {
    is DebugTestSheet.Root -> listOf(
        DebugStatus("Consent", result.hasUserConsent),
        DebugStatus("Magisk granted", result.magiskGranted),
    )

    is DebugTestSheet.Shizuku -> listOf(
        DebugStatus("Installed", result.isInstalled),
        DebugStatus("Consent", result.hasUserConsent),
        DebugStatus("Granted", result.isGranted),
    )
}

@Composable
internal fun DebugTestResultSheet(
    sheet: DebugTestSheet,
    onDismiss: () -> Unit,
) {
    SdmModalBottomSheet(onDismiss = onDismiss) { dismiss ->
        val (icon, title) = when (sheet) {
            is DebugTestSheet.Root -> SdmIcons.Shield to "Root diagnostics"
            is DebugTestSheet.Shizuku -> SdmIcons.Shizuku to "Shizuku diagnostics"
        }
        val output = when (sheet) {
            is DebugTestSheet.Root -> sheet.result.serviceLaunched
            is DebugTestSheet.Shizuku -> sheet.result.serviceLaunched
        }
        DebugTestResultSheetContent(
            icon = icon,
            title = title,
            statuses = sheet.statuses(),
            output = output,
            onClose = { dismiss {} },
        )
    }
}

@Composable
private fun DebugTestResultSheetContent(
    icon: ImageVector,
    title: String,
    statuses: List<DebugStatus>,
    output: String?,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            statuses.forEach { DebugStatusChip(it) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Service output",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DebugOutputBlock(output)

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onClose) {
                Text(text = stringResource(CommonR.string.general_close_action))
            }
        }
    }
}

@Composable
internal fun DebugStatusChip(status: DebugStatus) {
    val container = when (status.state) {
        true -> MaterialTheme.colorScheme.primaryContainer
        false -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status.state) {
        true -> MaterialTheme.colorScheme.onPrimaryContainer
        false -> MaterialTheme.colorScheme.onErrorContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dot = when (status.state) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.outline
    }
    val word = when (status.state) {
        true -> "yes"
        false -> "no"
        null -> "unknown"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${status.label}: $word",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DebugOutputBlock(output: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (output == null) {
            Text(
                text = "Timed out after 10s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            SelectionContainer {
                Text(
                    text = output.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

@Preview2
@Composable
private fun DebugTestResultSheetOkPreview() {
    PreviewWrapper {
        DebugTestResultSheetContent(
            icon = SdmIcons.Shield,
            title = "Root diagnostics",
            statuses = listOf(
                DebugStatus("Consent", true),
                DebugStatus("Magisk granted", true),
            ),
            output = "BaseCheck:\nOK\nShellOps 'whoami':\nroot",
            onClose = {},
        )
    }
}

@Preview2
@Composable
private fun DebugTestResultSheetTimeoutPreview() {
    PreviewWrapper {
        DebugTestResultSheetContent(
            icon = SdmIcons.Shizuku,
            title = "Shizuku diagnostics",
            statuses = listOf(
                DebugStatus("Installed", true),
                DebugStatus("Consent", null),
                DebugStatus("Granted", false),
            ),
            output = null,
            onClose = {},
        )
    }
}
