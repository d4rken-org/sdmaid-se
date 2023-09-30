package eu.darken.sdmse.appcleaner.core.automation.specs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference

@JsonClass(generateAdapter = false)
enum class SpecRomType(override val label: CaString) : EnumPreference<SpecRomType> {
    @Json(name = "AUTO") AUTO(R.string.appcleaner_automation_romtype_auto_label.toCaString()),
    @Json(name = "ALCATEL") ALCATEL("Alcatel".toCaString()),
    @Json(name = "ANDROID_TV") ANDROID_TV("AndroidTV".toCaString()),
    @Json(name = "AOSP") AOSP("AOSP".toCaString()),
    @Json(name = "COLOROS") COLOROS("ColorOS".toCaString()),
    @Json(name = "FLYME") FLYME("Flyme".toCaString()),
    @Json(name = "HUAWEI") HUAWEI("Huawei".toCaString()),
    @Json(name = "LGE") LGE("LGE".toCaString()),
    @Json(name = "MIUI") MIUI("MIUI".toCaString()),
    @Json(name = "NUBIA") NUBIA("Nubia".toCaString()),
    @Json(name = "ONEPLUS") ONEPLUS("OnePlus".toCaString()),
    @Json(name = "REALME") REALME("Realme".toCaString()),
    @Json(name = "SAMSUNG") SAMSUNG("Samsung".toCaString()),
    @Json(name = "VIVO") VIVO("VIVO".toCaString()),
    ;
}