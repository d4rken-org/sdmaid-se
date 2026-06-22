package eu.darken.sdmse.common.files

import androidx.annotation.StringRes

@get:StringRes
val FileType.labelRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> eu.darken.sdmse.common.R.string.file_type_directory
        FileType.SYMBOLIC_LINK -> eu.darken.sdmse.common.R.string.file_type_symbolic_link
        FileType.FILE -> eu.darken.sdmse.common.R.string.file_type_file
        FileType.UNKNOWN -> eu.darken.sdmse.common.R.string.file_type_unknown
    }
