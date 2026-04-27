package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledTonalActionButton

import java.io.File

data class DebugRecorderDashboardCardItem(
    val webpageTool: WebpageTool,
    val isRecording: Boolean,
    val currentLogDir: File?,
    val onToggleRecording: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun DebugRecorderDashboardCard(item: DebugRecorderDashboardCardItem) {
    var showConsent by remember { mutableStateOf(false) }

    if (showConsent) {
        RecorderConsentDialog(
            onStartRecording = item.onToggleRecording,
            onOpenPrivacyPolicy = { item.webpageTool.open(SdmSeLinks.PRIVACY_POLICY) },
            onDismiss = { showConsent = false },
        )
    }

    DashboardCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(UiR.drawable.ic_bug_report),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (item.isRecording) R.string.debug_debuglog_recording_progress else R.string.support_debuglog_label,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (item.isRecording) {
                item.currentLogDir?.let { "${it.path}/" }.orEmpty()
            } else {
                stringResource(R.string.support_debuglog_desc)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        DashboardFilledTonalActionButton(
            onClick = {
                if (item.isRecording) {
                    item.onToggleRecording()
                } else {
                    showConsent = true
                }
            },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_baseline_save_24),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(
                text = stringResource(
                    if (item.isRecording) R.string.debug_debuglog_stop_action else R.string.debug_debuglog_record_action,
                ),
            )
        }
    }
}

@Preview2
@Composable
private fun DebugRecorderDashboardCardPreview() {
    val context = LocalContext.current
    PreviewWrapper {
        DebugRecorderDashboardCard(
            item = DebugRecorderDashboardCardItem(
                webpageTool = WebpageTool(context),
                isRecording = false,
                currentLogDir = null,
                onToggleRecording = {},
            ),
        )
    }
}
