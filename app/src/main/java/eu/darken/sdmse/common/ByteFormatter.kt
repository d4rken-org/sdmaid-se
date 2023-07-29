package eu.darken.sdmse.common

import java.text.DecimalFormatSymbols
import java.util.Locale

object ByteFormatter {
    fun stripSizeUnit(formattedSize: String): Double? {
        val ds = DecimalFormatSymbols(Locale.getDefault()).decimalSeparator
        val matcher = Regex("^(\\d+(?:$ds\\d+)?)\\s*?\\w+\$")
        return matcher.find(formattedSize)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
}