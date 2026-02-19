package eu.darken.sdmse.common.forensics.csi.dalvik

import eu.darken.sdmse.common.forensics.Owner

interface DalvikCheck {

    data class Result(
        val owners: Set<Owner> = emptySet(),
        val hasKnownUnknownOwner: Boolean = false,
    ) {
        fun isEmpty() = owners.isEmpty() && !hasKnownUnknownOwner
    }
}