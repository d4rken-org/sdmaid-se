package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.files.core.matches

@JsonClass(generateAdapter = true)
data class PathExclusion(
    @Json(name = "path") val path: APath,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Path {
    override suspend fun match(candidate: APath): Boolean {
        return candidate.matches(path) || path.isAncestorOf(candidate)
    }

}
