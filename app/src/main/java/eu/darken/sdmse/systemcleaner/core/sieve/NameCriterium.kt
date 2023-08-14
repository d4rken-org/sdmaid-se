package eu.darken.sdmse.systemcleaner.core.sieve

data class NameCriterium(
    val name: String,
    val mode: Mode,
) : SieveCriterium {

    sealed interface Mode {
        data class Start(
            val ignoreCase: Boolean = true,
        ) : Mode

        data class Contain(
            val ignoreCase: Boolean = true,
        ) : Mode

        data class End(
            val ignoreCase: Boolean = true,
        ) : Mode

        data class Match(
            val ignoreCase: Boolean = true,
        ) : Mode
    }
}