package eu.darken.sdmse.common.pkgs

object PkgInventoryCheck {

    val SANITY_PKGS = setOf(
        "android",
        "com.android.cts.ctsshim",
    )

    fun check(packageNames: Collection<String>, ownPackage: String): Result = when {
        packageNames.isEmpty() -> Result.Empty
        !packageNames.contains(ownPackage) -> Result.MissingOwn
        packageNames.none { it in SANITY_PKGS } -> Result.MissingCore
        else -> Result.Valid
    }

    sealed interface Result {
        data object Valid : Result
        data object Empty : Result
        data object MissingOwn : Result
        data object MissingCore : Result
    }
}
