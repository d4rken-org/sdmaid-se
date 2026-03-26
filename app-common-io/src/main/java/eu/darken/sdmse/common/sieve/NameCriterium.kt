package eu.darken.sdmse.common.sieve

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@Parcelize
data class NameCriterium(
    val name: String,
    override val mode: Mode,
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
        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Start(
            val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("CONTAIN")
        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Contain(
            val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("END")
        @JsonClass(generateAdapter = true)
        @Parcelize
        data class End(
            val ignoreCase: Boolean = true,
        ) : Mode

        @Serializable @SerialName("EQUAL")
        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Equal(
            val ignoreCase: Boolean = true,
        ) : Mode
    }

    companion object {
        val MOSHI_ADAPTER_FACTORY = PolymorphicJsonAdapterFactory.of(Mode::class.java, "type")
            .withSubtype(Mode.Start::class.java, "START")
            .withSubtype(Mode.Contain::class.java, "CONTAIN")
            .withSubtype(Mode.End::class.java, "END")
            .withSubtype(Mode.Equal::class.java, "MATCH")!!
    }
}