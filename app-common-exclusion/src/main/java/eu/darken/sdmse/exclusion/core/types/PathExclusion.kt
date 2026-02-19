package eu.darken.sdmse.exclusion.core.types

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.matches

@JsonClass(generateAdapter = true)
data class PathExclusion(
    @Json(name = "path") val path: APath,
    @Json(name = "tags") override val tags: Set<Exclusion.Tag> = setOf(Exclusion.Tag.GENERAL)
) : Exclusion.Path {

    override val id: ExclusionId
        get() = createId(path)

    override val label: CaString
        get() = path.userReadablePath

    override suspend fun match(candidate: APath): Boolean {
        // path = dirA/dirB excludes dirA/dirB and dirA/dirB/file
        val match = candidate.matches(path) || path.isAncestorOf(candidate)
        if (match) log(TAG, VERBOSE) { "Exclusion match: $candidate <- $this " }
        return match
    }

    companion object {
        fun createId(path: APath): ExclusionId = "${PathExclusion::class.simpleName}-${path.path}"
        private val TAG = logTag("Exclusion", "Path")

    }
}
