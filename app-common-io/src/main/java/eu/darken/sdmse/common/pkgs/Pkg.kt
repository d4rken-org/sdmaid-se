package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.pkgs.pkgops.PkgOps

interface Pkg {

    val packageName: String

    val versionCode: Long

    val packageType: Type

  suspend fun getLabel(pkgOps: PkgOps): String?

    @Throws(Exception::class)
    fun <T> tryField(fieldName: String): T?

    enum class Type {
        NORMAL, INSTANT
    }
}
