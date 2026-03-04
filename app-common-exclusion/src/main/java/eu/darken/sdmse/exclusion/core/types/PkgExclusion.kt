package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.caString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.getLabel2

@JsonClass(generateAdapter = true)
data class PkgExclusion(
    @Json(name = "pkgId") val pkgId: Pkg.Id,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Pkg {

    override val id: ExclusionId
        get() = createId(pkgId)

    override val label: CaString
        get() = caString { it.packageManager.getLabel2(pkgId) ?: pkgId.name }

    override suspend fun match(candidate: Pkg.Id): Boolean {
        val match = pkgId == candidate
        if (match) log(TAG, VERBOSE) { "Exclusion match: $candidate <- $this " }
        return match
    }

    companion object {
        fun createId(pkgId: Pkg.Id): ExclusionId = "${PkgExclusion::class.simpleName}-${pkgId.name}"
        private val TAG = logTag("Exclusion", "Pkg")
    }
}
