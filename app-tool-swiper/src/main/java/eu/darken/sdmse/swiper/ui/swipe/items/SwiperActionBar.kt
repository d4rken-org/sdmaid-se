package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.swiper.R

@Composable
internal fun SwiperActionBar(
    canUndo: Boolean,
    swapDirections: Boolean,
    hasCurrentItem: Boolean,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    onUndo: () -> Unit,
    onSkip: () -> Unit,
    onSkipLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Outer left: DELETE by default, KEEP when swapped.
            FloatingActionButton(
                onClick = if (swapDirections) onKeep else onDelete,
                containerColor = if (swapDirections) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (swapDirections) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            ) {
                if (swapDirections) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.swiper_keep_action),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(CommonR.string.general_delete_action),
                    )
                }
            }

            AnimatedVisibility(visible = canUndo) {
                SmallFloatingActionButton(
                    onClick = onUndo,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = stringResource(CommonR.string.general_undo_action),
                    )
                }
            }

            // Skip (mini) — needs both onClick AND onLongClick, so render a custom Surface
            // shaped/styled like a SmallFloatingActionButton.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        enabled = hasCurrentItem,
                        onClick = onSkip,
                        onLongClick = onSkipLongPress,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SwipeIcon(
                        painter = painterResource(R.drawable.ic_baseline_skip_next_24),
                        contentDescription = stringResource(R.string.swiper_skip_action),
                    )
                }
            }

            // Outer right: KEEP by default, DELETE when swapped.
            FloatingActionButton(
                onClick = if (swapDirections) onDelete else onKeep,
                containerColor = if (swapDirections) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (swapDirections) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            ) {
                if (swapDirections) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(CommonR.string.general_delete_action),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(R.string.swiper_keep_action),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeIcon(
    painter: Painter,
    contentDescription: String?,
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = LocalContentColor.current,
    )
}
