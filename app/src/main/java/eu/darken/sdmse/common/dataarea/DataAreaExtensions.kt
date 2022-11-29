package eu.darken.sdmse.common.dataarea


val DataAreaType.isPublic
    get() = DataAreaType.PUBLIC_LOCATIONS.contains(this)


fun DataArea.hasFlags(vararg lookup: DataArea.Flag): Boolean = flags.containsAll(lookup.toList())