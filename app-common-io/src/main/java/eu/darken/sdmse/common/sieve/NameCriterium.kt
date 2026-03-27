package eu.darken.sdmse.common.sieve

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class NameCriterium(
    @SerialName("name") val name: String,
    @SerialName("mode") override val mode: Mode,
) : SieveCriterium {

    fun match(target: String): Boolean = when (mode) {
        is Mode.Start -> target.startsWith(name, ignoreCase = mode.ignoreCase)
        is Mode.End -> target.endsWith(name, ignoreCase = mode.ignoreCase)
        is Mode.Contain -> target.contains(name, ignoreCase = mode.ignoreCase)
        is Mode.Equal -> target.equals(name, ignoreCase = mode.ignoreCase)
    }

    @Serializable
    sealed interface Mode : SieveCriterium.Mode {

        @Serializable @SerialName("START")
        @Parcelize
        data class Start(
            @SerialName("ignoreCase") val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("CONTAIN")
        @Parcelize
        data class Contain(
            @SerialName("ignoreCase") val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("END")
        @Parcelize
        data class End(
            @SerialName("ignoreCase") val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("MATCH")
        @Parcelize
        data class Equal(
            @SerialName("ignoreCase") val ignoreCase: Boolean = true,
        ) : Mode
    }

}