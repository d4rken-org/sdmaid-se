package eu.darken.sdmse.common.preferences

import eu.darken.sdmse.common.ca.CaString

interface EnumPreference<T : Enum<T>> {
    val label: CaString
}