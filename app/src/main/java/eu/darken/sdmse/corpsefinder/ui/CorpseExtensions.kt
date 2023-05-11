package eu.darken.sdmse.corpsefinder.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.corpsefinder.core.filter.AppAsecFileCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppLibCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppSourceCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppSourcePrivateCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.DalvikCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PrivateDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicMediaCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicObbCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.SdcardCorpseFilter
import kotlin.reflect.KClass

@get:StringRes
val <T : CorpseFilter> KClass<T>.labelRes: Int
    get() = when (this) {
        SdcardCorpseFilter::class -> R.string.corpsefinder_filter_sdcard_label
        PublicMediaCorpseFilter::class -> R.string.corpsefinder_filter_publicmedia_label
        PublicDataCorpseFilter::class -> R.string.corpsefinder_filter_publicdata_label
        PublicObbCorpseFilter::class -> R.string.corpsefinder_filter_publicobb_label
        PrivateDataCorpseFilter::class -> R.string.corpsefinder_filter_privatedata_label
        DalvikCorpseFilter::class -> R.string.corpsefinder_filter_dalvik_label
        AppLibCorpseFilter::class -> R.string.corpsefinder_filter_applib_label
        AppSourceCorpseFilter::class -> R.string.corpsefinder_filter_appsource_label
        AppSourcePrivateCorpseFilter::class -> R.string.corpsefinder_filter_appsource_private_label
        AppAsecFileCorpseFilter::class -> R.string.corpsefinder_filter_appasec_label
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

@get:DrawableRes
val <T : CorpseFilter> KClass<T>.iconRes: Int
    get() = when (this) {
        SdcardCorpseFilter::class -> R.drawable.ic_sd_storage
        PublicMediaCorpseFilter::class -> R.drawable.ic_sd_storage
        PublicDataCorpseFilter::class -> R.drawable.ic_sd_storage
        PublicObbCorpseFilter::class -> R.drawable.ic_game_controller_24
        PrivateDataCorpseFilter::class -> R.drawable.ic_incognito_circle_24
        DalvikCorpseFilter::class -> R.drawable.ic_turbine_24
        AppLibCorpseFilter::class -> eu.darken.sdmse.common.io.R.drawable.ic_baseline_local_library_24
        AppSourceCorpseFilter::class -> R.drawable.ic_app_extra_24
        AppSourcePrivateCorpseFilter::class -> R.drawable.ic_folder_key_24
        AppAsecFileCorpseFilter::class -> R.drawable.ic_folder_key_24
        else -> R.drawable.ghost
    }