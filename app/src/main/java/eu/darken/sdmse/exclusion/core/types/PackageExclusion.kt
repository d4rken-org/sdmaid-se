package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getLabel2

@JsonClass(generateAdapter = true)
data class PackageExclusion(
    @Json(name = "pkgId") val pkgId: Pkg.Id,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Package {

    override val id: String
        get() = "${this.javaClass}-${pkgId.name}"

    override val label: CaString
        get() = caString { it.packageManager.getLabel2(pkgId) ?: it.getString(R.string.exclusion_type_package) }

    override suspend fun match(candidate: Pkg.Id): Boolean {
        return pkgId == candidate
    }

}
