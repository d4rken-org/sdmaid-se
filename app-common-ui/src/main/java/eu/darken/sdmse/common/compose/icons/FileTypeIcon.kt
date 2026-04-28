package eu.darken.sdmse.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.files.FileType

val FileType.icon: ImageVector
    get() = when (this) {
        FileType.DIRECTORY -> Icons.Outlined.Folder
        FileType.SYMBOLIC_LINK -> Icons.Outlined.Link
        FileType.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
        FileType.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    }
