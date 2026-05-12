package eu.darken.sdmse.common.theming

import androidx.compose.material3.ColorScheme

object ThemeColorProvider {

    fun getLightColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> SdmSeColorsGreen.lightScheme(style)
        ThemeColor.SCHOLAR -> SdmSeColorsScholar.lightScheme(style)
        ThemeColor.SUNSET -> SdmSeColorsSunset.lightScheme(style)
        ThemeColor.AMOLED -> SdmSeColorsAmoled.lightScheme(style)
        ThemeColor.COBALT -> SdmSeColorsCobalt.lightScheme(style)
        ThemeColor.CINNABAR -> SdmSeColorsCinnabar.lightScheme(style)
    }

    fun getDarkColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> SdmSeColorsGreen.darkScheme(style)
        ThemeColor.SCHOLAR -> SdmSeColorsScholar.darkScheme(style)
        ThemeColor.SUNSET -> SdmSeColorsSunset.darkScheme(style)
        ThemeColor.AMOLED -> SdmSeColorsAmoled.darkScheme(style)
        ThemeColor.COBALT -> SdmSeColorsCobalt.darkScheme(style)
        ThemeColor.CINNABAR -> SdmSeColorsCinnabar.darkScheme(style)
    }
}
