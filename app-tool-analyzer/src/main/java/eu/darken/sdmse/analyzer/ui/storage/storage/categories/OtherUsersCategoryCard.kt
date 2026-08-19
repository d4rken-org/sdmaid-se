package eu.darken.sdmse.analyzer.ui.storage.storage.categories

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.R
import eu.darken.sdmse.analyzer.core.storage.categories.OtherUsersCategory
import eu.darken.sdmse.analyzer.ui.storage.preview.previewDeviceStorage
import eu.darken.sdmse.analyzer.ui.storage.preview.previewOtherUsersCategory
import eu.darken.sdmse.analyzer.ui.storage.storage.StorageContentViewModel
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlin.math.roundToInt

@Composable
internal fun OtherUsersCategoryCard(
    modifier: Modifier = Modifier,
    row: StorageContentViewModel.Row.OtherUsers,
    onUserClick: (OtherUsersCategory.UserEntry) -> Unit = {},
) {
    val context = LocalContext.current
    val storage = row.storage
    val content = row.category
    val percentUsed: Int = if (storage.spaceUsed > 0L) {
        ((content.spaceUsed.toDouble() / storage.spaceUsed.toDouble()) * 100).toInt()
    } else 0
    val usedText = Formatter.formatShortFileSize(context, content.spaceUsed)
    val groupSizes = content.groups.associate { it.id to it.groupSize }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.TwoTone.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.analyzer_storage_content_type_otherusers_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.analyzer_storage_content_type_otherusers_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            content.users.forEach { user ->
                OtherUserRow(
                    user = user,
                    size = groupSizes[user.groupId],
                    onClick = { onUserClick(user) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LinearProgressIndicator(
                    progress = { (percentUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
                val quantity = ByteFormatter.stripSizeUnit(usedText)?.roundToInt() ?: 1
                Text(
                    text = pluralStringResource(R.plurals.analyzer_space_used, quantity, usedText),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun OtherUserRow(
    modifier: Modifier = Modifier,
    user: OtherUsersCategory.UserEntry,
    size: Long?,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    // Only app data is exact on the stats tier, and nothing is exact while the user's areas are
    // locked. Say so instead of presenting a partial number as this user's total storage.
    val hint = when {
        !user.appDataKnown -> stringResource(R.string.analyzer_storage_content_type_otherusers_user_unknown)
        !user.sharedMediaKnown -> stringResource(R.string.analyzer_storage_content_type_otherusers_user_partial)
        else -> null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (user.isBrowsable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.label.get(context),
                style = MaterialTheme.typography.bodyMedium,
            )
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (user.appDataKnown && size != null) {
            Text(
                text = Formatter.formatShortFileSize(context, size),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (user.isBrowsable) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun OtherUsersCategoryCardPreview() {
    val storage = previewDeviceStorage()
    PreviewWrapper {
        OtherUsersCategoryCard(
            row = StorageContentViewModel.Row.OtherUsers(
                storage = storage,
                category = previewOtherUsersCategory(storageId = storage.id),
            ),
        )
    }
}
