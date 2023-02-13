package eu.darken.sdmse.corpsefinder.ui

import android.content.Context
import eu.darken.sdmse.corpsefinder.core.Corpse

fun Corpse.getTypeLabel(context: Context): String {
    return filterType.simpleName!!
}