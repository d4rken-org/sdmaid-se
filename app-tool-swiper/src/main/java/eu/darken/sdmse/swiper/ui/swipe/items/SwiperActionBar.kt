package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Scaffold's bottomBar slot is NOT auto-padded for the nav bar inset; with
            // edge-to-edge enabled the bar would otherwise draw under the system nav bar.
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Outer left: DELETE by default, KEEP when swapped.
        LabeledAction(
            label = stringResource(
                if (swapDirections) R.string.swiper_keep_action
                else CommonR.string.general_delete_action
            ),
        ) {
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
                Icon(
                    imageVector = if (swapDirections) Icons.TwoTone.Favorite else Icons.TwoTone.Delete,
                    contentDescription = null,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Undo slot is always reserved so Skip stays put when Undo isn't available.
        LabeledAction(
            label = stringResource(CommonR.string.general_undo_action),
            modifier = Modifier.alpha(if (canUndo) 1f else 0f),
        ) {
            SmallFloatingActionButton(
                onClick = { if (canUndo) onUndo() },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Restore,
                    contentDescription = null,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Skip (mini) — needs both onClick AND onLongClick, so render a custom Surface
        // shaped/styled like a SmallFloatingActionButton.
        LabeledAction(label = stringResource(R.string.swiper_skip_action)) {
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
                        contentDescription = null,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Outer right: KEEP by default, DELETE when swapped.
        LabeledAction(
            label = stringResource(
                if (swapDirections) CommonR.string.general_delete_action
                else R.string.swiper_keep_action
            ),
        ) {
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
                Icon(
                    imageVector = if (swapDirections) Icons.TwoTone.Delete else Icons.TwoTone.Favorite,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SwiperActionBarPreview() {
    PreviewWrapper {
        SwiperActionBar(
            canUndo = true,
            swapDirections = false,
            hasCurrentItem = true,
            onDelete = {},
            onKeep = {},
            onUndo = {},
            onSkip = {},
            onSkipLongPress = {},
        )
    }
}

@Composable
private fun LabeledAction(
    label: String,
    modifier: Modifier = Modifier,
    button: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        button()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
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
