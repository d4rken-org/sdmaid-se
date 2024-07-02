package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.APath

interface AffectedPath {
    val reportId: ReportId
    val action: Action
    val path: APath

    enum class Action {
        DELETED,
        ;
    }
}

@get:DrawableRes val AffectedPath.Action.iconRes: Int
    get() = when (this) {
        AffectedPath.Action.DELETED -> R.drawable.ic_delete
    }