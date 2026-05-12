package eu.darken.sdmse.common.compose.tour

import androidx.compose.runtime.staticCompositionLocalOf

val LocalGuidedTourController = staticCompositionLocalOf<GuidedTourController> {
    error("GuidedTourController not provided")
}
