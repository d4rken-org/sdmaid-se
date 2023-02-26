package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.pkgs.Pkg

@JsonClass(generateAdapter = true)
data class PackageExclusion(
    @Json(name = "pkgId") val pkgId: Pkg.Id,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Package {

    override val id: String
        get() = "${this.javaClass}-${pkgId.name}"

    override suspend fun match(candidate: Pkg.Id): Boolean {
        return pkgId == candidate
    }

}
