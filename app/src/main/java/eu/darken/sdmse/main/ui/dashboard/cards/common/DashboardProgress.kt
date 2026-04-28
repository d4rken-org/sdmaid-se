package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
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
        }

        when (val count = progress.count) {
            is Progress.Count.None -> Unit
            is Progress.Count.Indeterminate -> {
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(32.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.matchParentSize(),
                        strokeWidth = 2.5.dp,
                    )
                }
            }

            is Progress.Count.Percent,
            is Progress.Count.Counter,
            is Progress.Count.Size -> {
                Spacer(modifier = Modifier.width(12.dp))
                val isDeterminate = count.current > 0L && count.max > 0L
                Box(modifier = Modifier.size(32.dp)) {
                    if (isDeterminate) {
                        val fraction = (count.current.toFloat() / count.max.toFloat()).coerceIn(0f, 1f)
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.matchParentSize(),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            text = "${(count.current * 100 / count.max).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.matchParentSize(),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }
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
private fun ProgressContainerCounterZeroPreview() {
    PreviewWrapper {
        ProgressContainer(
            modifier = Modifier.width(280.dp),
            onClick = null,
            progress = Progress.Data(
                primary = "Starting scan…".toCaString(),
                secondary = "".toCaString(),
                count = Progress.Count.Counter(current = 0, max = 100),
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
