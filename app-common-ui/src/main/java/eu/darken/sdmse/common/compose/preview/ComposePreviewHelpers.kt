package eu.darken.sdmse.common.compose.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.NoOpGuidedTourAccess
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
            // Screens read LocalGuidedTourController, which is only provided by MainActivity at runtime. In
            // @Preview/inspection there's no provider, so supply a no-op to keep previews from hitting the
            // local's error() default. Gated on LocalInspectionMode so screen tests — which nest PreviewWrapper
            // inside their own CompositionLocalProvider(... provides mockController) — keep their mock.
            if (LocalInspectionMode.current) {
                CompositionLocalProvider(LocalGuidedTourController provides NoOpGuidedTourAccess) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}
