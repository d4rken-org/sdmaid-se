package eu.darken.sdmse.common

import androidx.annotation.StringRes

@get:StringRes
val easterEggProgressMsg: Int
    get() = when ((0..13).random()) {
        0 -> R.string.general_progress_loading_egg_0
        1 -> R.string.general_progress_loading_egg_1
        2 -> R.string.general_progress_loading_egg_2
        3 -> R.string.general_progress_loading_egg_3
        4 -> R.string.general_progress_loading_egg_4
        5 -> R.string.general_progress_loading_egg_5
        6 -> R.string.general_progress_loading_egg_6
        7 -> R.string.general_progress_loading_egg_7
        8 -> R.string.general_progress_loading_egg_8
        9 -> R.string.general_progress_loading_egg_9
        10 -> R.string.general_progress_loading_egg_10
        11 -> R.string.general_progress_loading_egg_11
        12 -> R.string.general_progress_loading_egg_12
        13 -> R.string.general_progress_loading_egg_13
        else -> throw IllegalArgumentException()
    }