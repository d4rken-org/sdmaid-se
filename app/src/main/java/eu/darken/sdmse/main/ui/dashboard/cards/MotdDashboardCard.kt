package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GTranslate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.motd.Motd
import eu.darken.sdmse.main.core.motd.MotdState
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledTonalActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton

import java.util.Locale
import java.util.UUID

private val URL_REGEX = Regex("""https?://\S+""")

data class MotdDashboardCardItem(
    val state: MotdState,
    val onPrimary: () -> Unit,
    val onTranslate: () -> Unit,
    val onDismiss: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun MotdDashboardCard(item: MotdDashboardCardItem) {
    DashboardCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.dashboard_motd_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (item.state.allowTranslation) {
                IconButton(onClick = item.onTranslate) {
                    Icon(
                        imageVector = Icons.Outlined.GTranslate,
                        contentDescription = null,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MotdBody(message = item.state.motd.message)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardFlatActionButton(onClick = item.onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item.state.motd.primaryLink != null) {
                DashboardFilledTonalActionButton(onClick = item.onPrimary) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                    Text(text = stringResource(CommonR.string.general_show_details_action))
                }
            }
        }
    }
}

@Composable
private fun MotdBody(message: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    val annotatedText = remember(message, linkColor) {
        buildAnnotatedString {
            append(message)
            URL_REGEX.findAll(message).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                addLink(
                    url = LinkAnnotation.Url(
                        url = match.value,
                        styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                        linkInteractionListener = { (it as? LinkAnnotation.Url)?.url?.let(uriHandler::openUri) },
                    ),
                    start = start,
                    end = end,
                )
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 24,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview2
@Composable
private fun MotdDashboardCardPreview() {
    PreviewWrapper {
        MotdDashboardCard(
            item = MotdDashboardCardItem(
                state = MotdState(
                    motd = Motd(
                        id = UUID.randomUUID(),
                        message = "Compose dashboard cards now live here. Check https://example.com for details.",
                        primaryLink = "https://example.com",
                    ),
                    locale = Locale.ENGLISH,
                ),
                onPrimary = {},
                onTranslate = {},
                onDismiss = {},
            ),
        )
    }
}
