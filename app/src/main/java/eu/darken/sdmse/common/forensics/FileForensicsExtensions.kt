package eu.darken.sdmse.common.forensics

import eu.darken.sdmse.common.files.APathLookup

suspend fun FileForensics.identifyArea(lookup: APathLookup<*>) = identifyArea(lookup.lookedUp)