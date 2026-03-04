package eu.darken.sdmse.common

import android.content.Context
import android.text.format.Formatter
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

object ByteFormatter {
    fun stripSizeUnit(formattedSize: String): Double? {
        val ds = DecimalFormatSymbols(Locale.getDefault()).decimalSeparator
        val match = Regex("^(\\d+(?:$ds\\d+)?)\\s*?.+\$").matchEntire(formattedSize) ?: return null
        val (value) = match.destructured
        return value.toDoubleOrNull()
    }

    fun formatSize(
        context: Context,
        size: Long,
        shortFormat: Boolean = true,
    ): Pair<String, Int> {
        val formatted = if (shortFormat) {
            Formatter.formatShortFileSize(context, size)
        } else {
            Formatter.formatFileSize(context, size)
        }
        val quantity = stripSizeUnit(formatted)?.roundToInt() ?: size.toInt()
        return formatted to quantity
    }
}