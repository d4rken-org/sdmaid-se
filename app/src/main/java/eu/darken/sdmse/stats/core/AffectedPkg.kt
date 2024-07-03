package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.pkgs.Pkg

interface AffectedPkg {
    val reportId: ReportId
    val action: Action
    val pkgId: Pkg.Id

    enum class Action {
        EXPORTED,
        STOPPED,
        ENABLED,
        DISABLED,
        DELETED,
        ;
    }
}

@get:DrawableRes val AffectedPkg.Action.iconRes: Int
    get() = when (this) {
        AffectedPkg.Action.EXPORTED -> R.drawable.ic_baseline_save_24
        AffectedPkg.Action.STOPPED -> R.drawable.ic_alert_octagon_outline_24
        AffectedPkg.Action.ENABLED -> R.drawable.ic_snowflake_off_24
        AffectedPkg.Action.DISABLED -> R.drawable.ic_snowflake_24
        AffectedPkg.Action.DELETED -> R.drawable.ic_delete
    }