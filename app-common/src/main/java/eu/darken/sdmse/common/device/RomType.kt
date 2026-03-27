package eu.darken.sdmse.common.device

import eu.darken.sdmse.common.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RomType(override val label: CaString) : EnumPreference<RomType> {
    @SerialName("AUTO") AUTO(R.string.general_rom_type_auto_label.toCaString()),
    @SerialName("ALCATEL") ALCATEL("Alcatel".toCaString()),
    @SerialName("ANDROID_TV") ANDROID_TV("AndroidTV".toCaString()),
    @SerialName("AOSP") AOSP("AOSP".toCaString()),
    @SerialName("COLOROS") COLOROS("ColorOS".toCaString()),
    @SerialName("FLYME") FLYMEOS("Flyme OS".toCaString()),
    @SerialName("HUAWEI") HUAWEI("Huawei".toCaString()),
    @SerialName("LGE") LGUX("LG UX".toCaString()),
    @SerialName("LINEAGE") LINEAGE("LINEAGE".toCaString()),
    @SerialName("MIUI") MIUI("MIUI".toCaString()),
    @SerialName("HYPEROS") HYPEROS("HyperOS".toCaString()),
    @SerialName("NUBIA") NUBIA("Nubia".toCaString()),
    @SerialName("ONEPLUS") OXYGENOS("OxygenOS".toCaString()),
    @SerialName("REALME") REALMEUI("Realme UI".toCaString()),
    @SerialName("SAMSUNG") ONEUI("OneUI".toCaString()),
    @SerialName("VIVO") FUNTOUCHOS("FuntouchOS".toCaString()),
    @SerialName("ORIGINOS") ORIGINOS("OriginOS".toCaString()),
    @SerialName("HONOR") HONOR("HONOR".toCaString()),
    @SerialName("DOOGEE") DOOGEE("DOOGEE".toCaString()),
    @SerialName("OUKITEL") OUKITEL("OUKITEL".toCaString()),
}
