package eu.darken.sdmse.stats.core

import androidx.annotation.DrawableRes
import eu.darken.sdmse.R

@get:DrawableRes val AffectedPath.Action.iconRes: Int
    get() = when (this) {
        AffectedPath.Action.DELETED -> R.drawable.ic_delete
        AffectedPath.Action.COMPRESSED -> R.drawable.ic_image_compress_24
    }
