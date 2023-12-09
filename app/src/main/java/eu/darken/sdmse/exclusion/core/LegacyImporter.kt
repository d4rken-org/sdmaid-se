package eu.darken.sdmse.exclusion.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PkgExclusion
import eu.darken.sdmse.exclusion.core.types.SegmentExclusion
import javax.inject.Inject

class LegacyImporter @Inject constructor(
    private val moshi: Moshi,
) {
    private val adapter by lazy { moshi.adapter<ImportContainer>() }

    private fun String.isValidPkgName(): Boolean {
        if (this.isEmpty()) return false
        if (this.startsWith(".")) return false
        if (this.endsWith(".")) return false
        if (this.contains("..")) return false

        return this.all { it.isLetterOrDigit() || it == '.' }
    }

    suspend fun tryConvert(rawExclusions: String): Set<Exclusion> {
        if (rawExclusions.isEmpty()) throw IllegalArgumentException("Exclusion data was empty")

        try {
            val container =
                adapter.fromJson(rawExclusions) ?: throw IllegalArgumentException("Exclusion data was empty")

            if (container.version < 6) throw IllegalArgumentException("SDM1 exclusions < V6 not supported")

            return container.exclusions
                .mapNotNull { holder ->
                    when (holder.type) {
                        ExclusionHolder.Type.SIMPLE_CONTAINS -> when {
                            holder.contains!!.isValidPkgName() -> PkgExclusion(
                                pkgId = holder.contains.toPkgId(),
                                tags = holder.tags.map { it.toSDM2Tag() }.toSet(),
                            )

                            else -> SegmentExclusion(
                                segments = holder.contains.toSegs(),
                                tags = holder.tags.map { it.toSDM2Tag() }.toSet(),
                                allowPartial = true,
                                ignoreCase = true,
                            )
                        }

                        ExclusionHolder.Type.REGEX -> {
                            log(TAG, INFO) { "Regex from SDM1 is not supported: $holder" }
                            null
                        }
                    }
                }
                .toSet()
        } catch (e: Exception) {
            log(TAG, WARN) { "Invalid legacy data: ${e.asLog()}" }
            throw IllegalArgumentException("Invalid SD Maid 1 exclusion data", e)
        }
    }


    @JsonClass(generateAdapter = true)
    data class ImportContainer(
        @Json(name = "exclusions") val exclusions: List<ExclusionHolder>,
        @Json(name = "version") val version: Int,
    )

    @JsonClass(generateAdapter = true)
    data class ExclusionHolder(
        @Json(name = "contains_string") val contains: String?,
        @Json(name = "regex_string") val regex: String?,
        @Json(name = "tags") val tags: Set<Tag>,
        @Json(name = "timestamp") val timestamp: Long,
        @Json(name = "type") val type: Type,
    ) {

        @JsonClass(generateAdapter = false)
        enum class Type {
            @Json(name = "SIMPLE_CONTAINS") SIMPLE_CONTAINS,
            @Json(name = "REGEX") REGEX,
        }

        @JsonClass(generateAdapter = false)
        enum class Tag {
            @Json(name = "GLOBAL") GLOBAL,
            @Json(name = "APPCONTROL") APPCONTROL,
            @Json(name = "CORPSEFINDER") CORPSEFINDER,
            @Json(name = "SYSTEMCLEANER") SYSTEMCLEANER,
            @Json(name = "APPCLEANER") APPCLEANER,
            @Json(name = "DUPLICATES") DUPLICATES,
            @Json(name = "DATABASES") DATABASES;

            fun toSDM2Tag(): Exclusion.Tag = when (this) {
                GLOBAL -> Exclusion.Tag.GENERAL
                APPCONTROL -> Exclusion.Tag.GENERAL
                CORPSEFINDER -> Exclusion.Tag.CORPSEFINDER
                SYSTEMCLEANER -> Exclusion.Tag.SYSTEMCLEANER
                APPCLEANER -> Exclusion.Tag.APPCLEANER
                DUPLICATES -> Exclusion.Tag.DEDUPLICATOR
                DATABASES -> Exclusion.Tag.GENERAL
            }
        }
    }

    companion object {
        private val TAG = logTag("Exclusion", "Importer", "Legacy")
    }
}