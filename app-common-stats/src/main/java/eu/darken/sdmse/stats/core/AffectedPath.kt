package eu.darken.sdmse.stats.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.graphics.vector.ImageVector

val AffectedPath.Action.icon: ImageVector
    get() = when (this) {
        AffectedPath.Action.DELETED -> Icons.Outlined.Delete
        AffectedPath.Action.COMPRESSED -> Icons.Outlined.Compress
    }
