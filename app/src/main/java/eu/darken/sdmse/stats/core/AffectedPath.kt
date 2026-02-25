package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR

@get:DrawableRes val AffectedPath.Action.iconRes: Int
    get() = when (this) {
        AffectedPath.Action.DELETED -> R.drawable.ic_delete
        AffectedPath.Action.COMPRESSED -> CommonR.drawable.ic_image_compress_24
    }
