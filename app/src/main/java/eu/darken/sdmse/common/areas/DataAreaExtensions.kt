package eu.darken.sdmse.common.areas


val DataArea.Type.isPublic
    get() = DataArea.Type.PUBLIC_LOCATIONS.contains(this)


val DataArea.Type.restrictedCharset: Boolean
    get() = isPublic

val DataArea.Type.isCaseInsensitive: Boolean
    get() = isPublic

fun DataArea.hasFlags(vararg lookup: DataArea.Flag): Boolean = flags.containsAll(lookup.toList())