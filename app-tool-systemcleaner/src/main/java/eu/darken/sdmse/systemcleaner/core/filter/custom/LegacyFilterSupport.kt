package eu.darken.sdmse.systemcleaner.core.filter.custom

import androidx.annotation.Keep
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.UUID
import javax.inject.Inject


class LegacyFilterSupport @Inject constructor(
    private val json: Json,
) {

    suspend fun import(payload: String): CustomFilterConfig? {
        log(TAG) { "Importing $payload" }

        val legacyFilter = try {
            json.decodeFromString<Filter>(payload)
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to import $payload: ${e.asLog()}" }
            return null
        }

        val pathCriteria = mutableSetOf<SegmentCriterium>()
        legacyFilter.possibleBasePathes
            ?.map {
                SegmentCriterium(
                    segments = it.toSegs(),
                    mode = SegmentCriterium.Mode.Start(allowPartial = true, ignoreCase = true)
                )
            }
            ?.forEach { pathCriteria.add(it) }
        legacyFilter.possiblePathContains
            ?.map {
                SegmentCriterium(
                    segments = it.toSegs(),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true, ignoreCase = true)
                )
            }
            ?.forEach { pathCriteria.add(it) }

        val nameCriteria = mutableSetOf<NameCriterium>()
        legacyFilter.possibleNameInits
            ?.map { NameCriterium(name = it, mode = NameCriterium.Mode.Start(ignoreCase = true)) }
            ?.forEach { nameCriteria.add(it) }

        legacyFilter.possibleNameEndings
            ?.map { NameCriterium(name = it, mode = NameCriterium.Mode.End(ignoreCase = true)) }
            ?.forEach { nameCriteria.add(it) }

        return CustomFilterConfig(
            label = legacyFilter.label,
            identifier = UUID.randomUUID().toString(),
            pathCriteria = pathCriteria,
            nameCriteria = nameCriteria,
            exclusionCriteria = legacyFilter.exclusions?.map {
                SegmentCriterium(
                    segments = it.toSegs(),
                    mode = SegmentCriterium.Mode.Contain(allowPartial = true, ignoreCase = true)
                )
            }?.toSet(),
            pathRegexes = legacyFilter.regex?.map {
                Regex(it)
            }?.toSet(),
            sizeMinimum = legacyFilter.minimumSize,
            sizeMaximum = legacyFilter.maximumSize,
            ageMinimum = legacyFilter.minimumAge?.let { Duration.ofMillis(it) },
            ageMaximum = legacyFilter.maximumAge?.let { Duration.ofMillis(it) },
            fileTypes = legacyFilter.targetType?.let {
                when (it) {
                    Filter.TargetType.FILE -> setOf(FileType.FILE)
                    Filter.TargetType.DIRECTORY -> setOf(FileType.DIRECTORY)
                    Filter.TargetType.UNDEFINED -> null
                }
            },
            areas = legacyFilter.locations?.mapNotNull {
                when (it) {
                    Filter.Location.SDCARD -> DataArea.Type.SDCARD
                    Filter.Location.PUBLIC_MEDIA -> DataArea.Type.PUBLIC_MEDIA
                    Filter.Location.PUBLIC_DATA -> DataArea.Type.PUBLIC_DATA
                    Filter.Location.PUBLIC_OBB -> DataArea.Type.PUBLIC_OBB
                    Filter.Location.PRIVATE_DATA -> DataArea.Type.PRIVATE_DATA
                    Filter.Location.APP_LIB -> DataArea.Type.APP_LIB
                    Filter.Location.APP_ASEC -> DataArea.Type.APP_ASEC
                    Filter.Location.APP_APP -> DataArea.Type.APP_APP
                    Filter.Location.APP_APP_PRIVATE -> DataArea.Type.APP_APP_PRIVATE
                    Filter.Location.DALVIK_DEX -> DataArea.Type.DALVIK_DEX
                    Filter.Location.DALVIK_PROFILE -> DataArea.Type.DALVIK_PROFILE
                    Filter.Location.SYSTEM -> DataArea.Type.SYSTEM
                    Filter.Location.SYSTEM_APP -> DataArea.Type.SYSTEM_APP
                    Filter.Location.SYSTEM_PRIV_APP -> DataArea.Type.SYSTEM_PRIV_APP
                    Filter.Location.DOWNLOAD_CACHE -> DataArea.Type.DOWNLOAD_CACHE
                    Filter.Location.DATA -> DataArea.Type.DATA
                    Filter.Location.DATA_SYSTEM -> DataArea.Type.DATA_SYSTEM
                    Filter.Location.DATA_SYSTEM_CE -> DataArea.Type.DATA_SYSTEM_CE
                    Filter.Location.DATA_SYSTEM_DE -> DataArea.Type.DATA_SYSTEM_DE
                    Filter.Location.PORTABLE -> DataArea.Type.PORTABLE
                    Filter.Location.OEM -> DataArea.Type.OEM
                    Filter.Location.DATA_SDEXT2 -> DataArea.Type.DATA_SDEXT2
                    Filter.Location.ROOT -> null
                    Filter.Location.VENDOR -> null
                    Filter.Location.MNT_SECURE_ASEC -> null
                    Filter.Location.UNKNOWN -> null
                }
            }?.toSet()?.takeIf { it.isNotEmpty() }
        )
    }

    @Serializable
    data class Filter(
        @SerialName("label") val label: String,
        @SerialName("targetType") val targetType: TargetType? = null,
        @SerialName("isEmpty") val isEmpty: Boolean? = null,
        @SerialName("locations") val locations: Set<Location>? = null,
        @SerialName("mainPath") val possibleBasePathes: Set<String>? = null,
        @SerialName("pathContains") val possiblePathContains: Set<String>? = null,
        @SerialName("possibleNameInits") val possibleNameInits: Set<String>? = null,
        @SerialName("possibleNameEndings") val possibleNameEndings: Set<String>? = null,
        @SerialName("exclusions") val exclusions: Set<String>? = null,
        @SerialName("regexes") val regex: Set<String>? = null,
        @SerialName("maximumSize") val maximumSize: Long? = null,
        @SerialName("minimumSize") val minimumSize: Long? = null,
        @SerialName("maximumAge") val maximumAge: Long? = null,
        @SerialName("minimumAge") val minimumAge: Long? = null,
    ) {

        @Serializable
        @Keep
        enum class TargetType {
            @SerialName("FILE") FILE,
            @SerialName("DIRECTORY") DIRECTORY,
            @SerialName("UNDEFINED") UNDEFINED,
        }

        @Serializable
        @Keep
        enum class Location(val raw: String) {
            @SerialName("SDCARD") SDCARD("SDCARD"),
            @SerialName("PUBLIC_MEDIA") PUBLIC_MEDIA("PUBLIC_MEDIA"),
            @SerialName("PUBLIC_DATA") PUBLIC_DATA("PUBLIC_DATA"),
            @SerialName("PUBLIC_OBB") PUBLIC_OBB("PUBLIC_OBB"),
            @SerialName("PRIVATE_DATA") PRIVATE_DATA("PRIVATE_DATA"),
            @SerialName("APP_LIB") APP_LIB("APP_LIB"),
            @SerialName("APP_ASEC") APP_ASEC("APP_ASEC"),
            @SerialName("MNT_SECURE_ASEC") MNT_SECURE_ASEC("MNT_SECURE_ASEC"),
            @SerialName("APP_APP") APP_APP("APP_APP"),
            @SerialName("APP_APP_PRIVATE") APP_APP_PRIVATE("APP_APP_PRIVATE"),
            @SerialName("DALVIK_DEX") DALVIK_DEX("DALVIK_DEX"),
            @SerialName("DALVIK_PROFILE") DALVIK_PROFILE("DALVIK_PROFILE"),
            @SerialName("SYSTEM_APP") SYSTEM_APP("SYSTEM_APP"),
            @SerialName("SYSTEM_PRIV_APP") SYSTEM_PRIV_APP("SYSTEM_PRIV_APP"),
            @SerialName("DOWNLOAD_CACHE") DOWNLOAD_CACHE("DOWNLOAD_CACHE"),
            @SerialName("SYSTEM") SYSTEM("SYSTEM"),
            @SerialName("DATA") DATA("DATA"),
            @SerialName("DATA_SYSTEM") DATA_SYSTEM("DATA_SYSTEM"),
            @SerialName("DATA_SYSTEM_CE") DATA_SYSTEM_CE("DATA_SYSTEM_CE"),
            @SerialName("DATA_SYSTEM_DE") DATA_SYSTEM_DE("DATA_SYSTEM_DE"),
            @SerialName("PORTABLE") PORTABLE("PORTABLE"),
            @SerialName("ROOT") ROOT("ROOT"),
            @SerialName("VENDOR") VENDOR("VENDOR"),
            @SerialName("OEM") OEM("OEM"),
            @SerialName("DATA_SDEXT2") DATA_SDEXT2("DATA_SDEXT2"),
            @SerialName("UNKNOWN") UNKNOWN("UNKNOWN"),
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Repo")
    }
}