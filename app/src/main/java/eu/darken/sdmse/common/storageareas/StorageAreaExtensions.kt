package eu.darken.sdmse.common.storageareas


val StorageArea.Type.isPublic
    get() = StorageArea.Type.PUBLIC_LOCATIONS.contains(this)


val StorageArea.Type.restrictedCharset: Boolean
    get() = isPublic

fun StorageArea.hasFlags(vararg lookup: StorageArea.Flag): Boolean = flags.containsAll(lookup.toList())