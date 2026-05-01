package eu.darken.sdmse.stats.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Compress
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.ui.graphics.vector.ImageVector

val AffectedPath.Action.icon: ImageVector
    get() = when (this) {
        AffectedPath.Action.DELETED -> Icons.TwoTone.Delete
        AffectedPath.Action.COMPRESSED -> Icons.TwoTone.Compress
    }
