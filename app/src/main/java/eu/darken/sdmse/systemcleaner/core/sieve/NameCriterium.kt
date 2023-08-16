package eu.darken.sdmse.systemcleaner.core.sieve

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class NameCriterium(
    val name: String,
    override val mode: Mode,
) : SieveCriterium {

    sealed interface Mode : SieveCriterium.Mode {

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Start(
            val ignoreCase: Boolean = true,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class Contain(
            val ignoreCase: Boolean = true,
        ) : Mode

        @JsonClass(generateAdapter = true)
        @Parcelize
        data class End(
            val ignoreCase: Boolean = true,
        ) : Mode

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