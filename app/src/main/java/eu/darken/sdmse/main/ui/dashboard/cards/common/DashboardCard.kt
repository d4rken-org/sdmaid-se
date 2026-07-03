package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColorFor(containerColor),
    )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
    if (onClick != null) {
        // Use the clickable Card overload so the hover/ripple indication is clipped to the
        // card's rounded shape. Applying Modifier.clickable to the outer modifier instead
        // draws the indication against the rectangular bounds (square highlight on the corners).
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = colors,
            content = cardContent,
        )
    } else {
        Card(
            modifier = cardModifier,
            colors = colors,
            content = cardContent,
        )
    }
}

@Preview2
@Composable
private fun DashboardCardPreview() {
    PreviewWrapper {
        DashboardCard {
            Text(
                text = "Dashboard card",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cards live in the surface-container-low layer by default and stretch to fill the list width.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview2
@Composable
private fun DashboardCardClickablePreview() {
    PreviewWrapper {
        DashboardCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            onClick = {},
        ) {
            Text(
                text = "Clickable variant",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pass an onClick to make the whole card a tap target.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
