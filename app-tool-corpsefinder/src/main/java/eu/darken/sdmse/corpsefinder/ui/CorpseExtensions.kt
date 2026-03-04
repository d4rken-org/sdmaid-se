package eu.darken.sdmse.corpsefinder.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.corpsefinder.R
import eu.darken.sdmse.corpsefinder.core.filter.AppAsecFileCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppLibCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppSourceCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.AppSourcePrivateCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.ArtProfilesCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.DalvikCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PrivateDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicDataCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicMediaCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.PublicObbCorpseFilter
import eu.darken.sdmse.corpsefinder.core.filter.SdcardCorpseFilter
import kotlin.reflect.KClass
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.R as CommonR

@get:StringRes
val <T : CorpseFilter> KClass<T>.labelRes: Int
    get() = when (this) {
        SdcardCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_sdcard_label
        PublicMediaCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_publicmedia_label
        PublicDataCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_publicdata_label
        PublicObbCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_publicobb_label
        PrivateDataCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_privatedata_label
        DalvikCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_dalvik_label
        ArtProfilesCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_artprofiles_label
        AppLibCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_applib_label
        AppSourceCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_appsource_label
        AppSourcePrivateCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_appsource_private_label
        AppAsecFileCorpseFilter::class -> eu.darken.sdmse.corpsefinder.R.string.corpsefinder_filter_appasec_label
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

@get:DrawableRes
val <T : CorpseFilter> KClass<T>.iconRes: Int
    get() = when (this) {
        SdcardCorpseFilter::class -> UiR.drawable.ic_sd_storage
        PublicMediaCorpseFilter::class -> UiR.drawable.ic_sd_storage
        PublicDataCorpseFilter::class -> UiR.drawable.ic_sd_storage
        PublicObbCorpseFilter::class -> UiR.drawable.ic_game_controller_24
        PrivateDataCorpseFilter::class -> UiR.drawable.ic_incognito_circle_24
        DalvikCorpseFilter::class -> UiR.drawable.ic_turbine_24
        ArtProfilesCorpseFilter::class -> UiR.drawable.ic_artboard_24
        AppLibCorpseFilter::class -> eu.darken.sdmse.common.io.R.drawable.ic_baseline_local_library_24
        AppSourceCorpseFilter::class -> CommonR.drawable.ic_app_extra_24
        AppSourcePrivateCorpseFilter::class -> UiR.drawable.ic_folder_key_24
        AppAsecFileCorpseFilter::class -> UiR.drawable.ic_folder_key_24
        else -> CommonR.drawable.ghost
    }