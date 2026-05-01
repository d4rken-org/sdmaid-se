package eu.darken.sdmse.swiper.ui.status.items

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.ErrorOutline
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import androidx.compose.ui.res.stringResource

@Composable
fun SwiperStatusRow(
    modifier: Modifier = Modifier,
    item: SwipeItem,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReset: () -> Unit,
    onQuickKeep: () -> Unit,
    onQuickDelete: () -> Unit,
) {
    val context = LocalContext.current
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    val stripeColor = when (item.decision) {
        SwipeDecision.DELETE, SwipeDecision.DELETE_FAILED -> MaterialTheme.colorScheme.error
        SwipeDecision.KEEP, SwipeDecision.DELETED -> MaterialTheme.colorScheme.primary
        SwipeDecision.UNDECIDED -> Color.Transparent
    }

    val fileName = item.lookup.name
    val fullPath = item.lookup.userReadablePath.get(context)
    val parentPath = fullPath.removeSuffix(fileName)
    val (sizeText, _) = ByteFormatter.formatSize(context, item.lookup.size)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(stripeColor),
        )
        Spacer(Modifier.width(12.dp))
        FilePreviewImage(
            lookup = item.lookup,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (parentPath.isNotEmpty()) {
                Text(
                    text = parentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sizeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DecisionIndicator(decision = item.decision)
        Spacer(Modifier.width(4.dp))
        QuickActions(
            decision = item.decision,
            enabled = !selectionActive,
            onReset = onReset,
            onQuickKeep = onQuickKeep,
            onQuickDelete = onQuickDelete,
        )
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun DecisionIndicator(decision: SwipeDecision) {
    val (icon, tint, description) = when (decision) {
        SwipeDecision.KEEP, SwipeDecision.DELETED -> Triple(
            Icons.TwoTone.Favorite,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.swiper_keep_action),
        )

        SwipeDecision.DELETE -> Triple(
            Icons.TwoTone.Delete,
            MaterialTheme.colorScheme.error,
            stringResource(CommonR.string.general_delete_action),
        )

        SwipeDecision.DELETE_FAILED -> Triple(
            Icons.TwoTone.ErrorOutline,
            MaterialTheme.colorScheme.error,
            null,
        )

        SwipeDecision.UNDECIDED -> return
    }
    Icon(
        imageVector = icon,
        tint = tint,
        contentDescription = description,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun QuickActions(
    decision: SwipeDecision,
    enabled: Boolean,
    onReset: () -> Unit,
    onQuickKeep: () -> Unit,
    onQuickDelete: () -> Unit,
) {
    if (decision == SwipeDecision.UNDECIDED) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onQuickKeep, enabled = enabled) {
                Icon(
                    imageVector = Icons.TwoTone.Favorite,
                    contentDescription = stringResource(R.string.swiper_keep_action),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onQuickDelete, enabled = enabled) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    } else {
        IconButton(onClick = onReset, enabled = enabled) {
            Icon(
                imageVector = Icons.TwoTone.Restore,
                contentDescription = stringResource(CommonR.string.general_reset_action),
            )
        }
    }
}
