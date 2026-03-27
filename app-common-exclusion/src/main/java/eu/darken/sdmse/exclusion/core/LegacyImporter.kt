package eu.darken.sdmse.exclusion.core

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

class LegacyImporter @Inject constructor(
    private val json: Json,
) {

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
            val container = json.decodeFromString<ImportContainer>(rawExclusions)

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


    @Serializable
    data class ImportContainer(
        @SerialName("exclusions") val exclusions: List<ExclusionHolder>,
        @SerialName("version") val version: Int,
    )

    @Serializable
    data class ExclusionHolder(
        @SerialName("contains_string") val contains: String? = null,
        @SerialName("regex_string") val regex: String? = null,
        @SerialName("tags") val tags: Set<Tag>,
        @SerialName("timestamp") val timestamp: Long,
        @SerialName("type") val type: Type,
    ) {

        @Serializable
        enum class Type {
            @SerialName("SIMPLE_CONTAINS") SIMPLE_CONTAINS,
            @SerialName("REGEX") REGEX,
        }

        @Serializable
        enum class Tag {
            @SerialName("GLOBAL") GLOBAL,
            @SerialName("APPCONTROL") APPCONTROL,
            @SerialName("CORPSEFINDER") CORPSEFINDER,
            @SerialName("SYSTEMCLEANER") SYSTEMCLEANER,
            @SerialName("APPCLEANER") APPCLEANER,
            @SerialName("DUPLICATES") DUPLICATES,
            @SerialName("DATABASES") DATABASES;

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