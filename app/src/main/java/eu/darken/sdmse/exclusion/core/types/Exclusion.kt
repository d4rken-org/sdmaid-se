package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.serialization.NameBasedPolyJsonAdapterFactory

sealed interface Exclusion {

    val id: String

    val tags: Set<Tag>

    val label: CaString

    @JsonClass(generateAdapter = false)
    enum class Tag {
        @Json(name = "GENERAL") GENERAL,
        @Json(name = "CORPSEFINDER") CORPSEFINDER,
        @Json(name = "SYSTEMCLEANER") SYSTEMCLEANER,
        @Json(name = "APPCLEANER") APPCLEANER
    }

    interface Package : Exclusion {
        suspend fun match(candidate: Pkg.Id): Boolean
    }

    interface Path : Exclusion {
        suspend fun match(candidate: APath): Boolean
    }

    companion object {
        val MOSHI_FACTORY: NameBasedPolyJsonAdapterFactory<Exclusion> =
            NameBasedPolyJsonAdapterFactory.of(Exclusion::class.java)
                .withSubtype(PackageExclusion::class.java, "pkgId")
                .withSubtype(PathExclusion::class.java, "path")
//                .skipLabelSerialization()
    }
}