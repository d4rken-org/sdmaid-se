package eu.darken.sdmse.systemcleaner.ui.customfilter.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

@Composable
internal fun LiveSearchSheetContent(
    state: CustomFilterEditorViewModel.LiveSearchState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        state.firstInit -> stringResource(SystemCleanerR.string.systemcleaner_customfilter_editor_livesearch_label)
                        else -> pluralStringResource(
                            CommonR.plurals.result_x_items,
                            state.matches.size,
                            state.matches.size,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val secondary = when {
                    state.firstInit -> stringResource(CommonR.string.general_progress_ready)
                    state.progress == null -> stringResource(CommonR.string.general_progress_done)
                    else -> state.progress.primary.get(context)
                }
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.progress != null) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        if (state.matches.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.matches, key = { it.id }) { match ->
                    LiveSearchRow(match)
                }
            }
        }
    }
}

@Composable
private fun LiveSearchRow(match: LiveSearchMatch) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(match.lookup.fileType.iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = match.lookup.userReadablePath.get(context),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
