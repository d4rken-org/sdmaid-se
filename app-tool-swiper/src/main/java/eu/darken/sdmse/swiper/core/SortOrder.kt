package eu.darken.sdmse.swiper.core

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference

enum class SortOrder(override val label: CaString) : EnumPreference<SortOrder> {
    OLDEST_FIRST(R.string.general_sort_oldest_first.toCaString()),
    NEWEST_FIRST(R.string.general_sort_newest_first.toCaString()),
    NAME_ASC(R.string.general_sort_name_asc.toCaString()),
    SIZE_DESC(R.string.general_sort_size_desc.toCaString()),
    ;

    companion object {
        val DEFAULT = OLDEST_FIRST
    }
}
