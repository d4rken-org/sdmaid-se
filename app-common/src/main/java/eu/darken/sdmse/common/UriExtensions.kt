package eu.darken.sdmse.common

import android.net.Uri

fun Uri.dropLastColon(): Uri = Uri.parse(toString().removeSuffix(Uri.encode(":")))