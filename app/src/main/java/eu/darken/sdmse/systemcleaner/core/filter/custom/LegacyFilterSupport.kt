package eu.darken.sdmse.systemcleaner.core.filter.custom

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.toSegs
import eu.darken.sdmse.systemcleaner.core.sieve.NameCriterium
import eu.darken.sdmse.systemcleaner.core.sieve.SegmentCriterium
import java.time.Duration
import java.util.UUID
import javax.inject.Inject


class LegacyFilterSupport @Inject constructor(
    private val moshi: Moshi,
) {
    private val adapter by lazy { moshi.adapter(Filter::class.java) }

    suspend fun import(payload: String): CustomFilterConfig? {
        log(TAG) { "Importing $payload" }

        val legacyFilter = try {
            adapter.fromJson(payload)!!
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

    @JsonClass(generateAdapter = true)
    data class Filter(
        @Json(name = "label") val label: String,
        @Json(name = "targetType") val targetType: TargetType?,
        @Json(name = "isEmpty") val isEmpty: Boolean?,
        @Json(name = "locations") val locations: Set<Location>?,
        @Json(name = "mainPath") val possibleBasePathes: Set<String>?,
        @Json(name = "pathContains") val possiblePathContains: Set<String>?,
        @Json(name = "possibleNameInits") val possibleNameInits: Set<String>?,
        @Json(name = "possibleNameEndings") val possibleNameEndings: Set<String>?,
        @Json(name = "exclusions") val exclusions: Set<String>?,
        @Json(name = "regexes") val regex: Set<String>?,
        @Json(name = "maximumSize") val maximumSize: Long?,
        @Json(name = "minimumSize") val minimumSize: Long?,
        @Json(name = "maximumAge") val maximumAge: Long?,
        @Json(name = "minimumAge") val minimumAge: Long?,
    ) {

        //    @Json(name = "version") private int version = VERSION;
        //    @Json(name = "identifier") private String identifier;
        //    @Json(name = "color") private String color;
        //    @Json(name = "description") private String description;
        //    @Nullable @Json(name = "rootOnly") private Boolean rootOnly;

        @Keep
        @JsonClass(generateAdapter = false)
        enum class TargetType {
            @Json(name = "FILE") FILE,
            @Json(name = "DIRECTORY") DIRECTORY,
            @Json(name = "UNDEFINED") UNDEFINED,
        }

        @Keep
        @JsonClass(generateAdapter = false)
        enum class Location(val raw: String) {
            @Json(name = "SDCARD") SDCARD("SDCARD"),
            @Json(name = "PUBLIC_MEDIA") PUBLIC_MEDIA("PUBLIC_MEDIA"),
            @Json(name = "PUBLIC_DATA") PUBLIC_DATA("PUBLIC_DATA"),
            @Json(name = "PUBLIC_OBB") PUBLIC_OBB("PUBLIC_OBB"),
            @Json(name = "PRIVATE_DATA") PRIVATE_DATA("PRIVATE_DATA"),
            @Json(name = "APP_LIB") APP_LIB("APP_LIB"),
            @Json(name = "APP_ASEC") APP_ASEC("APP_ASEC"),
            @Json(name = "MNT_SECURE_ASEC") MNT_SECURE_ASEC("MNT_SECURE_ASEC"),
            @Json(name = "APP_APP") APP_APP("APP_APP"),
            @Json(name = "APP_APP_PRIVATE") APP_APP_PRIVATE("APP_APP_PRIVATE"),
            @Json(name = "DALVIK_DEX") DALVIK_DEX("DALVIK_DEX"),
            @Json(name = "DALVIK_PROFILE") DALVIK_PROFILE("DALVIK_PROFILE"),
            @Json(name = "SYSTEM_APP") SYSTEM_APP("SYSTEM_APP"),
            @Json(name = "SYSTEM_PRIV_APP") SYSTEM_PRIV_APP("SYSTEM_PRIV_APP"),
            @Json(name = "DOWNLOAD_CACHE") DOWNLOAD_CACHE("DOWNLOAD_CACHE"),
            @Json(name = "SYSTEM") SYSTEM("SYSTEM"),
            @Json(name = "DATA") DATA("DATA"),
            @Json(name = "DATA_SYSTEM") DATA_SYSTEM("DATA_SYSTEM"),
            @Json(name = "DATA_SYSTEM_CE") DATA_SYSTEM_CE("DATA_SYSTEM_CE"),
            @Json(name = "DATA_SYSTEM_DE") DATA_SYSTEM_DE("DATA_SYSTEM_DE"),
            @Json(name = "PORTABLE") PORTABLE("PORTABLE"),
            @Json(name = "ROOT") ROOT("ROOT"),
            @Json(name = "VENDOR") VENDOR("VENDOR"),
            @Json(name = "OEM") OEM("OEM"),
            @Json(name = "DATA_SDEXT2") DATA_SDEXT2("DATA_SDEXT2"),
            @Json(name = "UNKNOWN") UNKNOWN("UNKNOWN"),
        }
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "CustomFilter", "Repo")
    }
}