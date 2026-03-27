package eu.darken.sdmse.common.areas

import android.os.Parcelable
import androidx.annotation.Keep
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.user.UserHandle2
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class DataArea(
    val path: APath,
    val type: Type,
    val label: CaString = path.path.toCaString(),
    val flags: Set<Flag> = emptySet(),
    /**
     * -1 location has no user seperation
     * 0 admin user/owner
     * X other users
     */
    val userHandle: UserHandle2,
) {

    enum class Flag {
        PRIMARY,
        EMULATED
    }

    @Serializable
    @Keep @Parcelize
    enum class Type(val raw: String) : Parcelable {
        @SerialName("SDCARD") SDCARD("SDCARD"),
        @SerialName("PUBLIC_MEDIA") PUBLIC_MEDIA("PUBLIC_MEDIA"),
        @SerialName("PUBLIC_DATA") PUBLIC_DATA("PUBLIC_DATA"),
        @SerialName("PUBLIC_OBB") PUBLIC_OBB("PUBLIC_OBB"),
        @SerialName("PRIVATE_DATA") PRIVATE_DATA("PRIVATE_DATA"),
        @SerialName("APP_LIB") APP_LIB("APP_LIB"),
        @SerialName("APP_ASEC") APP_ASEC("APP_ASEC"),
        @SerialName("APP_APP") APP_APP("APP_APP"),
        @SerialName("APP_APP_PRIVATE") APP_APP_PRIVATE("APP_APP_PRIVATE"),
        @SerialName("DALVIK_DEX") DALVIK_DEX("DALVIK_DEX"),
        @SerialName("DALVIK_PROFILE") DALVIK_PROFILE("DALVIK_PROFILE"),
        @SerialName("ART_PROFILE") ART_PROFILE("ART_PROFILE"),
        @SerialName("DOWNLOAD_CACHE") DOWNLOAD_CACHE("DOWNLOAD_CACHE"),
        @SerialName("DATA") DATA("DATA"),
        @SerialName("DATA_MISC") DATA_MISC("DATA_MISC"),
        @SerialName("DATA_VENDOR") DATA_VENDOR("DATA_VENDOR"),
        @SerialName("DATA_SYSTEM") DATA_SYSTEM("DATA_SYSTEM"),

        /**
         * Base directory for per-user system directory, credential encrypted.
         */
        @SerialName("DATA_SYSTEM_CE") DATA_SYSTEM_CE("DATA_SYSTEM_CE"),

        /**
         * Base directory for per-user system directory, device encrypted.
         */
        @SerialName("DATA_SYSTEM_DE") DATA_SYSTEM_DE("DATA_SYSTEM_DE"),

        @SerialName("PORTABLE") PORTABLE("PORTABLE"),

        /**
         * Link2SD https://play.google.com/store/apps/details?id=com.buak.Link2SD
         * Apps2SD https://play.google.com/store/apps/details?id=com.a0soft.gphone.app2sd
         */
        @SerialName("DATA_SDEXT2") DATA_SDEXT2("DATA_SDEXT2"),

        @SerialName("SYSTEM") SYSTEM("SYSTEM"),
        @SerialName("SYSTEM_APP") SYSTEM_APP("SYSTEM_APP"),
        @SerialName("SYSTEM_PRIV_APP") SYSTEM_PRIV_APP("SYSTEM_PRIV_APP"),
        @SerialName("OEM") OEM("OEM"),

        @SerialName("APEX") APEX("APEX"),

        ;

        companion object {
            fun fromRaw(raw: String): Type =
                values().singleOrNull { it.raw == raw } ?: throw IllegalArgumentException("Unknown location TAG: $raw")

            @JvmField val PUBLIC_LOCATIONS = listOf(
                SDCARD, PUBLIC_DATA, PUBLIC_OBB, PUBLIC_MEDIA, PORTABLE
            )
        }
    }
}

