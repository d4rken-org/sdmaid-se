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
    @Json(name = "FLYME") FLYMEOS("Flyme OS".toCaString()),
    @Json(name = "HUAWEI") HUAWEI("Huawei".toCaString()),
    @Json(name = "LGE") LGUX("LG UX".toCaString()),
    @Json(name = "LINEAGE") LINEAGE("LINEAGE".toCaString()),
    @Json(name = "MIUI") MIUI("MIUI".toCaString()),
    @Json(name = "HYPEROS") HYPEROS("HyperOS".toCaString()),
    @Json(name = "NUBIA") NUBIA("Nubia".toCaString()),
    @Json(name = "ONEPLUS") OXYGENOS("OxygenOS".toCaString()),
    @Json(name = "REALME") REALMEUI("Realme UI".toCaString()),
    @Json(name = "SAMSUNG") ONEUI("OneUI".toCaString()),
    @Json(name = "VIVO") FUNTOUCHOS("FuntouchOS".toCaString()),
    @Json(name = "ORIGINOS") ORIGINOS("OriginOS".toCaString()),
    @Json(name = "HONOR") HONOR("HONOR".toCaString()),
    @Json(name = "DOOGEE") DOOGEE("DOOGEE".toCaString()),
    @Json(name = "OUKITEL") OUKITEL("OUKITEL".toCaString()),
    ;
}