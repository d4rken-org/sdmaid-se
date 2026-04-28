package eu.darken.sdmse.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Folder
import androidx.compose.material.icons.twotone.Link
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.files.FileType

val FileType.icon: ImageVector
    get() = when (this) {
        FileType.DIRECTORY -> Icons.TwoTone.Folder
        FileType.SYMBOLIC_LINK -> Icons.TwoTone.Link
        FileType.FILE -> Icons.AutoMirrored.TwoTone.InsertDriveFile
        FileType.UNKNOWN -> Icons.AutoMirrored.TwoTone.HelpOutline
    }
