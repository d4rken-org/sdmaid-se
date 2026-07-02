package eu.darken.sdmse.automation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.SdmMascot
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.automation.R as AutomationR
import eu.darken.sdmse.common.R as CommonR

internal data class AutomationOverlayState(
    val title: CaString = "".toCaString(),
    val subtitle: CaString = "".toCaString(),
    val progress: Progress.Data? = null,
)

@Composable
internal fun AutomationOverlay(
    modifier: Modifier = Modifier,
    state: AutomationOverlayState,
    isTv: Boolean = false,
    showHomeCancelHint: Boolean = false,
    onCancel: () -> Unit,
) {
    val progress = state.progress ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.8f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            SdmMascot(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .padding(horizontal = 32.dp),
            )

            Text(
                text = stringResource(
                    when {
                        isTv && showHomeCancelHint -> AutomationR.string.automation_screenoverlay_explanation_tv
                        // TV without a resolvable launcher: no reachable cancel, so don't promise one.
                        isTv -> AutomationR.string.automation_screenoverlay_explanation_nocancel
                        else -> AutomationR.string.automation_screenoverlay_explanation
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            ControlCard(
                state = state,
                progress = progress,
                isTv = isTv,
                onCancel = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 64.dp),
            )
        }
    }
}

@Composable
private fun ControlCard(
    modifier: Modifier = Modifier,
    state: AutomationOverlayState,
    progress: Progress.Data,
    isTv: Boolean = false,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current

    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.AccessibilityNew,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = state.title.get(ctx),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.subtitle.get(ctx),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ProgressIndicator(
                count = progress.count,
                modifier = Modifier.size(40.dp),
            )
        }

        // Always reserve a fixed-height text region (1 primary + 3 secondary lines) so the
        // bottom-anchored card doesn't resize and "jump" as the progress text changes.
        Text(
            text = progress.primary.get(ctx),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = progress.secondary.get(ctx),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            minLines = 3,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (isTv) {
            // No touchscreen and the overlay window isn't D-pad focusable, so the button would be
            // unreachable. Cancel happens via the Home button (see the explanation text) instead.
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Cancel,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(CommonR.string.general_cancel_action))
            }
        }
    }
}

@Composable
private fun ProgressIndicator(
    modifier: Modifier = Modifier,
    count: Progress.Count,
) {
    val ctx = LocalContext.current
    when (count) {
        is Progress.Count.None -> Unit

        is Progress.Count.Indeterminate -> {
            CircularProgressIndicator(modifier = modifier)
        }

        is Progress.Count.Counter, is Progress.Count.Percent -> {
            val current = count.current
            val max = count.max
            // current==0 with a known max is a valid START state (determinate ring at 0%), not
            // indeterminate. Only fall back to the spinner when the total (max) is unknown.
            val useIndeterminate = max == 0L
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                if (useIndeterminate) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    CircularProgressIndicator(
                        progress = { (current.toFloat() / max.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    count.displayValue(ctx)?.takeIf { it.isNotEmpty() }?.let { value ->
                        Text(text = value, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        is Progress.Count.Size -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                count.displayValue(ctx)?.takeIf { it.isNotEmpty() }?.let { value ->
                    Text(text = value, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AutomationOverlayIndeterminatePreview() {
    PreviewWrapper {
        AutomationOverlay(
            state = AutomationOverlayState(
                title = "AppCleaner".toCaString(),
                subtitle = "Clearing caches…".toCaString(),
                progress = Progress.Data(
                    primary = "com.example.app".toCaString(),
                    secondary = "/storage/emulated/0/Android/data/com.example.app/cache".toCaString(),
                    count = Progress.Count.Indeterminate(),
                ),
            ),
            onCancel = {},
        )
    }
}

@Preview2
@Composable
private fun AutomationOverlayPercentPreview() {
    PreviewWrapper {
        AutomationOverlay(
            state = AutomationOverlayState(
                title = "AppCleaner".toCaString(),
                subtitle = "Clearing caches…".toCaString(),
                progress = Progress.Data(
                    primary = "com.example.app".toCaString(),
                    secondary = "/storage/emulated/0/Android/data/com.example.app/cache".toCaString(),
                    count = Progress.Count.Percent(current = 33, max = 100),
                ),
            ),
            onCancel = {},
        )
    }
}

@Preview2
@Composable
private fun AutomationOverlayTvPreview() {
    PreviewWrapper {
        AutomationOverlay(
            state = AutomationOverlayState(
                title = "AppCleaner".toCaString(),
                subtitle = "Clearing caches…".toCaString(),
                progress = Progress.Data(
                    primary = "com.example.app".toCaString(),
                    secondary = "/storage/emulated/0/Android/data/com.example.app/cache".toCaString(),
                    count = Progress.Count.Indeterminate(),
                ),
            ),
            isTv = true,
            showHomeCancelHint = true,
            onCancel = {},
        )
    }
}
