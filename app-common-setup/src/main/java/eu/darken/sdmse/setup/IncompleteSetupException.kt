package eu.darken.sdmse.setup

class IncompleteSetupException(val setupTypes: Set<SetupModule.Type>) : Exception() {

    constructor(setupType: SetupModule.Type) : this(setOf(setupType))
}
