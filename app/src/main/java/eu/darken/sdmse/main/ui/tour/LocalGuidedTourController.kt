package eu.darken.sdmse.main.ui.tour

import androidx.compose.runtime.staticCompositionLocalOf

val LocalGuidedTourController = staticCompositionLocalOf<GuidedTourController> {
    error("GuidedTourController not provided")
}
