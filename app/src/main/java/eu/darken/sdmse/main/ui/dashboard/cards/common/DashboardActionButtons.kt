package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

internal val DashboardActionIconSpacing = 4.dp
private val DashboardActionButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
private val DashboardIconActionButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

@Composable
internal fun DashboardFlatActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardIconActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardIconActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardFilledActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardFilledTonalActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Composable
internal fun DashboardOutlinedActionButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = DashboardActionButtonPadding,
        content = content,
    )
}

@Preview2
@Composable
private fun DashboardActionButtonsPreview() {
    PreviewWrapper {
        Column(
            modifier = Modifier.width(260.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DashboardFilledActionButton(onClick = {}) {
                Text(text = "Filled")
            }
            DashboardFilledTonalActionButton(onClick = {}) {
                Text(text = "Filled tonal")
            }
            DashboardOutlinedActionButton(onClick = {}) {
                Text(text = "Outlined")
            }
            DashboardFlatActionButton(onClick = {}) {
                Text(text = "Flat")
            }
            DashboardIconActionButton(onClick = {}) {
                Text(text = "Icon")
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = "(tighter padding)")
            }
        }
    }
}
