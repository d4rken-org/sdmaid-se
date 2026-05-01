package eu.darken.sdmse.stats.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DoNotDisturb
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.SettingsBackupRestore
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector

val AffectedPkg.Action.icon: ImageVector
    get() = when (this) {
        AffectedPkg.Action.EXPORTED -> Icons.TwoTone.Save
        AffectedPkg.Action.STOPPED -> Icons.TwoTone.WarningAmber
        AffectedPkg.Action.ENABLED -> Icons.TwoTone.DoNotDisturb
        AffectedPkg.Action.DISABLED -> Icons.TwoTone.AcUnit
        AffectedPkg.Action.DELETED -> Icons.TwoTone.Delete
        AffectedPkg.Action.ARCHIVED -> Icons.TwoTone.Archive
        AffectedPkg.Action.RESTORED -> Icons.TwoTone.SettingsBackupRestore
    }
