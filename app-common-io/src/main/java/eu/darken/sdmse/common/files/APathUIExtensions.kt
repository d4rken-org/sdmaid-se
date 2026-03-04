package eu.darken.sdmse.common.files

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.common.R as CommonR

@get:DrawableRes
val FileType.iconRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> CommonR.drawable.ic_folder
        FileType.SYMBOLIC_LINK -> CommonR.drawable.ic_file_link
        FileType.FILE -> CommonR.drawable.ic_file
        FileType.UNKNOWN -> CommonR.drawable.file_question
    }

@get:StringRes
val FileType.labelRes: Int
    get() = when (this) {
        FileType.DIRECTORY -> eu.darken.sdmse.common.R.string.file_type_directory
        FileType.SYMBOLIC_LINK -> eu.darken.sdmse.common.R.string.file_type_symbolic_link
        FileType.FILE -> eu.darken.sdmse.common.R.string.file_type_file
        FileType.UNKNOWN -> eu.darken.sdmse.common.R.string.file_type_unknown
    }