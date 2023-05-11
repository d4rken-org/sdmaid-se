package eu.darken.sdmse.common.files

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R

@get:DrawableRes
val FileType.iconRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> R.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> R.drawable.ic_file_link
        FileType.FILE -> R.drawable.ic_file
        FileType.UNKNOWN -> R.drawable.file_question
    }

@get:StringRes
val FileType.labelRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> eu.darken.sdmse.common.R.string.file_type_directory
        FileType.SYMBOLIC_LINK -> eu.darken.sdmse.common.R.string.file_type_symbolic_link
        FileType.FILE -> eu.darken.sdmse.common.R.string.file_type_file
        FileType.UNKNOWN -> eu.darken.sdmse.common.R.string.file_type_unknown
    }