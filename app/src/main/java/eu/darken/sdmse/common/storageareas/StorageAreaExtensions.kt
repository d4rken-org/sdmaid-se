package eu.darken.sdmse.common.storageareas


val StorageArea.Type.isPublic
    get() = StorageArea.Type.PUBLIC_LOCATIONS.contains(this)


fun StorageArea.hasFlags(vararg lookup: StorageArea.Flag): Boolean = flags.containsAll(lookup.toList())