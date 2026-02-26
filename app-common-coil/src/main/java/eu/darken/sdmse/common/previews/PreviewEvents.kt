package eu.darken.sdmse.common.previews

import eu.darken.sdmse.common.files.APathLookup

sealed class PreviewEvents {
    data class ConfirmDeletion(val items: Collection<APathLookup<*>>) : PreviewEvents()
}
