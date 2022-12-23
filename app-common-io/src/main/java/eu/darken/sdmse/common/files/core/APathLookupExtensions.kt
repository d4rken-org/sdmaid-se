package eu.darken.sdmse.common.files.core


val APathLookup<*>.isDirectory: Boolean
    get() = fileType == FileType.DIRECTORY

val APathLookup<*>.isSymlink: Boolean
    get() = fileType == FileType.SYMBOLIC_LINK

val APathLookup<*>.isFile: Boolean
    get() = fileType == FileType.FILE