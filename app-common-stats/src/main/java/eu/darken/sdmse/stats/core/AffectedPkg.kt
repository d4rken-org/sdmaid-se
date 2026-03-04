package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.common.ui.R as UiR

@get:DrawableRes val AffectedPkg.Action.iconRes: Int
    get() = when (this) {
        AffectedPkg.Action.EXPORTED -> UiR.drawable.ic_baseline_save_24
        AffectedPkg.Action.STOPPED -> UiR.drawable.ic_alert_octagon_outline_24
        AffectedPkg.Action.ENABLED -> UiR.drawable.ic_snowflake_off_24
        AffectedPkg.Action.DISABLED -> UiR.drawable.ic_snowflake_24
        AffectedPkg.Action.DELETED -> UiR.drawable.ic_delete
        AffectedPkg.Action.ARCHIVED -> eu.darken.sdmse.common.io.R.drawable.ic_archive_24
        AffectedPkg.Action.RESTORED -> UiR.drawable.ic_settings_backup_restore_24
    }
