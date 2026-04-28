package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.asComposable
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress

@Composable
internal fun ProgressContainer(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?,
    progress: Progress.Data?,
    resultPrimary: String?,
    resultSecondary: String?,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            when {
                progress != null -> DashboardProgress(progress)
                else -> {
                    resultPrimary?.takeUnless { it.isBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    resultSecondary?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardProgress(progress: Progress.Data) {
    val context = LocalContext.current
    Text(
        text = progress.primary.asComposable(),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    val secondary = progress.secondary.asComposable()
    if (secondary.isNotEmpty()) {
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (progress.count !is Progress.Count.None) {
        Spacer(modifier = Modifier.height(8.dp))
        when (val count = progress.count) {
            is Progress.Count.Indeterminate -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is Progress.Count.Percent -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.Counter -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.Size -> LinearProgressIndicator(
                progress = { if (count.max > 0) count.current.toFloat() / count.max.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth(),
            )

            is Progress.Count.None -> Unit
        }

        val countText = progress.count.displayValue(context).orEmpty()
        if (countText.isNotEmpty() && progress.count !is Progress.Count.Indeterminate) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview2
@Composable
private fun ProgressContainerIndeterminatePreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = null,
            progress = Progress.Data(
                primary = "Scanning…".toCaString(),
                secondary = "".toCaString(),
                count = Progress.Count.Indeterminate(),
            ),
            resultPrimary = null,
            resultSecondary = null,
        )
    }
}

@Preview2
@Composable
private fun ProgressContainerPercentPreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = null,
            progress = Progress.Data(
                primary = "Scanning files…".toCaString(),
                secondary = "Checking app caches".toCaString(),
                count = Progress.Count.Percent(current = 42L, max = 100L),
            ),
            resultPrimary = null,
            resultSecondary = null,
        )
    }
}

@Preview2
@Composable
private fun ProgressContainerLongPathPreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = null,
            progress = Progress.Data(
                primary = "Looking for orphaned data with a primary line that is also way too long to fit".toCaString(),
                secondary = "/storage/emulated/0/Android/data/com.example.someapp/files/cache/very/deeply/nested/path/that/wraps".toCaString(),
                count = Progress.Count.Counter(current = 18L, max = 250L),
            ),
            resultPrimary = null,
            resultSecondary = null,
        )
    }
}

@Preview2
@Composable
private fun ProgressContainerResultPreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = {},
            progress = null,
            resultPrimary = "Found 12 corpses (2.4 GB)",
            resultSecondary = "Last scan completed 5 minutes ago",
        )
    }
}

@Preview2
@Composable
private fun ProgressContainerResultPrimaryOnlyPreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = {},
            progress = null,
            resultPrimary = "Nothing to clean",
            resultSecondary = null,
        )
    }
}
