package eu.darken.sdmse.main.core.motd

import android.net.Uri
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale
import java.util.UUID

@JsonClass(generateAdapter = true)
data class MotdState(
    @Json(name = "motd") val motd: MotdApi.Motd,
    @Json(name = "locale") val locale: Locale,
) {
    val id: UUID
        get() = motd.id

    val allowTranslation: Boolean
        get() = Locale.getDefault().language != locale.language

    val translationUrl: String
        get() = "https://translate.google.com/?text=${Uri.encode(motd.message)}&sl=${locale.language}"
}
