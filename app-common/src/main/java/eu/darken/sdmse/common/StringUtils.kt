package eu.darken.sdmse.common


fun String?.hashCode(ignoreCase: Boolean): Int {
    if (this == null) return 0
    return if (ignoreCase) lowercase().hashCode() else hashCode()
}