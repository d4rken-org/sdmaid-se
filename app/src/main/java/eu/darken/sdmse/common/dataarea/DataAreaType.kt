package eu.darken.sdmse.common.dataarea

import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
enum class DataAreaType(val raw: String) : Parcelable {
    @Json(name = "SDCARD")
    SDCARD("SDCARD"),
    @Json(name = "PUBLIC_MEDIA")
    PUBLIC_MEDIA("PUBLIC_MEDIA"),
    @Json(name = "PUBLIC_DATA")
    PUBLIC_DATA("PUBLIC_DATA"),
    @Json(name = "PUBLIC_OBB")
    PUBLIC_OBB("PUBLIC_OBB"),
    @Json(name = "PRIVATE_DATA")
    PRIVATE_DATA("PRIVATE_DATA"),
    @Json(name = "APP_LIB")
    APP_LIB("APP_LIB"),
    @Json(name = "APP_ASEC")
    APP_ASEC("APP_ASEC"),
    @Json(name = "MNT_SECURE_ASEC")
    MNT_SECURE_ASEC("MNT_SECURE_ASEC"),
    @Json(name = "APP_APP")
    APP_APP("APP_APP"),
    @Json(name = "APP_APP_PRIVATE")
    APP_APP_PRIVATE("APP_APP_PRIVATE"),
    @Json(name = "DALVIK_DEX")
    DALVIK_DEX("DALVIK_DEX"),
    @Json(name = "DALVIK_PROFILE")
    DALVIK_PROFILE("DALVIK_PROFILE"),
    @Json(name = "SYSTEM_APP")
    SYSTEM_APP("SYSTEM_APP"),
    @Json(name = "SYSTEM_PRIV_APP")
    SYSTEM_PRIV_APP("SYSTEM_PRIV_APP"),
    @Json(name = "DOWNLOAD_CACHE")
    DOWNLOAD_CACHE("DOWNLOAD_CACHE"),
    @Json(name = "SYSTEM")
    SYSTEM("SYSTEM"),
    @Json(name = "DATA")
    DATA("DATA"),
    @Json(name = "DATA_SYSTEM")
    DATA_SYSTEM("DATA_SYSTEM"),

    /**
     * Base directory for per-user system directory, credential encrypted.
     */
    @Json(name = "DATA_SYSTEM_CE")
    DATA_SYSTEM_CE("DATA_SYSTEM_CE"),

    /**
     * Base directory for per-user system directory, device encrypted.
     */
    @Json(name = "DATA_SYSTEM_DE")
    DATA_SYSTEM_DE("DATA_SYSTEM_DE"),
    @Json(name = "PORTABLE")
    PORTABLE("PORTABLE"),
    @Json(name = "ROOT")
    ROOT("ROOT"),
    @Json(name = "VENDOR")
    VENDOR("VENDOR"),
    @Json(name = "OEM")
    OEM("OEM"),

    /**
     * Link2SD https://play.google.com/store/apps/details?id=com.buak.Link2SD
     * Apps2SD https://play.google.com/store/apps/details?id=com.a0soft.gphone.app2sd
     */
    @Json(name = "DATA_SDEXT2")
    DATA_SDEXT2("DATA_SDEXT2"),
    @Json(name = "UNKNOWN")
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromRaw(raw: String): DataAreaType =
            values().singleOrNull { it.raw == raw } ?: throw IllegalArgumentException("Unknown location TAG: $raw")

        @JvmField val PUBLIC_LOCATIONS = listOf(
            SDCARD,
            PUBLIC_DATA,
            PUBLIC_OBB,
            PUBLIC_MEDIA,
            PORTABLE
        )
    }
}