package eu.darken.sdmse.stats.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

val AffectedPkg.Action.icon: ImageVector
    get() = when (this) {
        AffectedPkg.Action.EXPORTED -> Icons.Outlined.Save
        AffectedPkg.Action.STOPPED -> Icons.Outlined.WarningAmber
        AffectedPkg.Action.ENABLED -> Icons.Outlined.DoNotDisturb
        AffectedPkg.Action.DISABLED -> Icons.Outlined.AcUnit
        AffectedPkg.Action.DELETED -> Icons.Outlined.Delete
        AffectedPkg.Action.ARCHIVED -> Icons.Outlined.Archive
        AffectedPkg.Action.RESTORED -> Icons.Outlined.SettingsBackupRestore
    }
