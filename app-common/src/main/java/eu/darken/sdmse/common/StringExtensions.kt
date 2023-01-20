package eu.darken.sdmse.common

import java.util.*

val rngString
    get() = UUID.randomUUID().toString()


fun String?.hashCode(ignoreCase: Boolean): Int {
    if (this == null) return 0
    return if (ignoreCase) lowercase().hashCode() else hashCode()
}


fun List<String>?.hashCode(ignoreCase: Boolean): Int {
    if (this == null) return 0
    return if (ignoreCase) this.map { it.lowercase() }.hashCode() else hashCode()
}