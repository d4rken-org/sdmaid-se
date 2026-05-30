package eu.darken.sdmse.common.compose.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import eu.darken.sdmse.common.theming.SdmSeTheme
import eu.darken.sdmse.common.theming.ThemeMode
import eu.darken.sdmse.common.theming.ThemeState
import eu.darken.sdmse.common.theming.ThemeStyle

@Composable
fun PreviewWrapper(
    theme: ThemeState = ThemeState(ThemeMode.SYSTEM, style = ThemeStyle.DEFAULT),
    content: @Composable () -> Unit,
) {
    SdmSeTheme(state = theme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
