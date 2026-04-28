package eu.darken.sdmse.corpsefinder.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.common.compose.icons.Artboard
import eu.darken.sdmse.common.compose.icons.Ghost
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.Turbine
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

val <T : CorpseFilter> KClass<T>.icon: ImageVector
    get() = when (this) {
        SdcardCorpseFilter::class -> Icons.Outlined.SdStorage
        PublicMediaCorpseFilter::class -> Icons.Outlined.SdStorage
        PublicDataCorpseFilter::class -> Icons.Outlined.SdStorage
        PublicObbCorpseFilter::class -> Icons.Outlined.SportsEsports
        PrivateDataCorpseFilter::class -> Icons.Outlined.VisibilityOff
        DalvikCorpseFilter::class -> SdmIcons.Turbine
        ArtProfilesCorpseFilter::class -> SdmIcons.Artboard
        AppLibCorpseFilter::class -> Icons.Outlined.LocalLibrary
        AppSourceCorpseFilter::class -> Icons.Outlined.Inventory2
        AppSourcePrivateCorpseFilter::class -> Icons.Outlined.FolderShared
        AppAsecFileCorpseFilter::class -> Icons.Outlined.FolderShared
        else -> SdmIcons.Ghost
    }
