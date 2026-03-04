package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.ui.R as UiR

@get:DrawableRes val AffectedPath.Action.iconRes: Int
    get() = when (this) {
        AffectedPath.Action.DELETED -> UiR.drawable.ic_delete
        AffectedPath.Action.COMPRESSED -> CommonR.drawable.ic_image_compress_24
    }
