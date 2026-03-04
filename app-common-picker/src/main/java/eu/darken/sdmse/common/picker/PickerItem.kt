package eu.darken.sdmse.common.picker

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APathLookup

data class PickerItem(
    val lookup: APathLookup<*>,
    val parent: PickerItem?,
    val dataArea: DataArea,
    val selected: Boolean,
    val selectable: Boolean,
)