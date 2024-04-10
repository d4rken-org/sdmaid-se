package eu.darken.sdmse.common.device

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference

@JsonClass(generateAdapter = false)
enum class RomType(override val label: CaString) : EnumPreference<RomType> {
    @Json(name = "AUTO") AUTO(R.string.general_rom_type_auto_label.toCaString()),
    @Json(name = "ALCATEL") ALCATEL("Alcatel".toCaString()),
    @Json(name = "ANDROID_TV") ANDROID_TV("AndroidTV".toCaString()),
    @Json(name = "AOSP") AOSP("AOSP".toCaString()),
    @Json(name = "COLOROS") COLOROS("ColorOS".toCaString()),
    @Json(name = "FLYME") FLYME("Flyme".toCaString()),
    @Json(name = "HUAWEI") HUAWEI("Huawei".toCaString()),
    @Json(name = "LGE") LGE("LGE".toCaString()),
    @Json(name = "LINEAGE") LINEAGE("LINEAGE".toCaString()),
    @Json(name = "MIUI") MIUI("MIUI".toCaString()),
    @Json(name = "NUBIA") NUBIA("Nubia".toCaString()),
    @Json(name = "ONEPLUS") ONEPLUS("OnePlus".toCaString()),
    @Json(name = "POCO") POCO("POCO".toCaString()),
    @Json(name = "REALME") REALME("Realme".toCaString()),
    @Json(name = "SAMSUNG") SAMSUNG("Samsung".toCaString()),
    @Json(name = "VIVO") VIVO("VIVO".toCaString()),
    @Json(name = "HONOR") HONOR("HONOR".toCaString()),
    ;
}