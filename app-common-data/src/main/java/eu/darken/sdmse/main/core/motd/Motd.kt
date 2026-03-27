@file:UseSerializers(UUIDSerializer::class)

package eu.darken.sdmse.main.core.motd

import eu.darken.sdmse.common.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.UUID

@Serializable
data class Motd(
    @SerialName("id") val id: UUID,
    @SerialName("message") val message: String,
    @SerialName("primaryLink") val primaryLink: String? = null,
    @SerialName("versionMinimum") val minimumVersion: Long? = null,
    @SerialName("versionMaximum") val maximumVersion: Long? = null,
)
