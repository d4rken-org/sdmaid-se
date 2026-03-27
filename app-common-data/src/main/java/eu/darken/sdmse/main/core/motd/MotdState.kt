@file:UseSerializers(LocaleSerializer::class)

package eu.darken.sdmse.main.core.motd

import android.net.Uri
import eu.darken.sdmse.common.serialization.LocaleSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.Locale
import java.util.UUID

@Serializable
data class MotdState(
    @SerialName("motd") val motd: Motd,
    @SerialName("locale") val locale: Locale,
) {
    val id: UUID
        get() = motd.id

    val allowTranslation: Boolean
        get() = Locale.getDefault().language != locale.language

    val translationUrl: String
        get() = "https://translate.google.com/?text=${Uri.encode(motd.message)}&sl=${locale.language}"
}
