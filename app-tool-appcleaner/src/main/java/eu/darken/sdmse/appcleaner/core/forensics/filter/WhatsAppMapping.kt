package eu.darken.sdmse.appcleaner.core.forensics.filter

import eu.darken.sdmse.common.areas.DataArea

data class WhatsAppMapping(
    val location: DataArea.Type,
    val pkg: String,
    val folder1: String,
    val folder2: String
)