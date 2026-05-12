package eu.darken.sdmse.common.theming

import androidx.compose.material3.ColorScheme

object ThemeColorProvider {

    fun getLightColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> SdmSeColorsGreen.lightScheme(style)
        ThemeColor.SCHOLAR -> SdmSeColorsScholar.lightScheme(style)
    }

    fun getDarkColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> SdmSeColorsGreen.darkScheme(style)
        ThemeColor.SCHOLAR -> SdmSeColorsScholar.darkScheme(style)
    }
}
