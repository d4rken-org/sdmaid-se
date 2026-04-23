package eu.darken.sdmse.common.areas

import eu.darken.sdmse.common.data.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import kotlinx.coroutines.flow.first


val DataArea.Type.isPublic
    get() = DataArea.Type.PUBLIC_LOCATIONS.contains(this)


val DataArea.Type.restrictedCharset: Boolean
    get() = isPublic

/**
 * https://source.android.com/devices/storage/traditional
 */
val DataArea.Type.isCaseInsensitive: Boolean
    get() = isPublic

fun DataArea.hasFlags(vararg lookup: DataArea.Flag): Boolean = flags.containsAll(lookup.toList())

suspend fun DataAreaManager.currentAreas(): Collection<DataArea> = state.first().areas

/**
 * The area roots users see as a whole storage volume (SD card, emulated shared storage, Android/data, Android/media,
 * Android/obb, OTG/portable). Selecting these as the source of a destructive scan effectively targets every file on
 * that volume — worth surfacing to the user before we start iterating.
 */
val DataArea.Type.isSensitiveRoot: Boolean
    get() = this in DataArea.Type.PUBLIC_LOCATIONS

val DataArea.isSensitiveRoot: Boolean
    get() = type.isSensitiveRoot

val DataArea.Type.label: CaString
    get() = when (this) {
        DataArea.Type.SDCARD -> R.string.area_type_sdcard_label.toCaString()
        DataArea.Type.PUBLIC_MEDIA -> R.string.area_type_public_media_label.toCaString()
        DataArea.Type.PUBLIC_DATA -> R.string.area_type_public_data_label.toCaString()
        DataArea.Type.PUBLIC_OBB -> R.string.area_type_public_obb_label.toCaString()
        DataArea.Type.PRIVATE_DATA -> R.string.area_type_private_data_label.toCaString()
        DataArea.Type.PORTABLE -> R.string.area_type_portable_label.toCaString()
        else -> this.raw.toCaString()
    }