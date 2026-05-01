package eu.darken.sdmse.swiper.ui.sessions.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.swiper.R

@Composable
fun SwiperSessionsUpgradeCard(
    modifier: Modifier = Modifier,
    freeVersionLimit: Int,
    freeSessionLimit: Int,
    onUpgrade: () -> Unit,
) {
    val filesLimit = pluralStringResource(
        R.plurals.swiper_sessions_upgrade_files_limit,
        freeVersionLimit,
        freeVersionLimit,
    )
    val sessionsLimit = pluralStringResource(
        R.plurals.swiper_sessions_upgrade_sessions_limit,
        freeSessionLimit,
        freeSessionLimit,
    )
    val body = stringResource(R.string.swiper_sessions_upgrade_body) +
        "\n• $filesLimit\n• $sessionsLimit"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                FilledTonalButton(onClick = onUpgrade) {
                    Text(stringResource(CommonR.string.general_upgrade_action))
                }
            }
        }
    }
}
