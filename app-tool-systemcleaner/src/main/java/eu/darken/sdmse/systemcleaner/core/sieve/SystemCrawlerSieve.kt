package eu.darken.sdmse.systemcleaner.core.sieve

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea
import eu.darken.sdmse.common.sieve.FileSieve
import eu.darken.sdmse.common.sieve.NameCriterium
import eu.darken.sdmse.common.sieve.SegmentCriterium
import eu.darken.sdmse.common.sieve.TypeCriterium
import java.time.Duration

class SystemCrawlerSieve @AssistedInject constructor(
    @Assisted val config: Config,
    private val fileForensics: FileForensics,
) : FileSieve {

    data class Result(
        val item: APathLookup<*>,
        val matches: Boolean,
        val areaInfo: AreaInfo? = null,
    )

    suspend fun match(subject: APathLookup<*>): Result {
        val nope = { Result(subject, matches = false) }

        // Directory or file?
        config.targetTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            if (subject.isFile && !types.contains(TypeCriterium.FILE)) {
                return nope()
            } else if (subject.isDirectory && !types.contains(TypeCriterium.DIRECTORY)) {
                return nope()
            }
        }

        config.maximumSize?.let {
            // Is our subject too large?
            if (subject.size > it) return nope()
        }

        config.minimumSize?.let {
            // Maybe it's too small
            if (subject.size < it) return nope()
        }

        if (config.maximumAge != null || config.minimumAge != null) {
            val nowMillis = System.currentTimeMillis()
            config.maximumAge?.let {
                if (nowMillis - subject.modifiedAt.toEpochMilli() > it.toMillis()) {
                    return nope()
                }
            }

            config.minimumAge?.let {
                if (nowMillis - subject.modifiedAt.toEpochMilli() < it.toMillis()) {
                    return nope()
                }
            }
        }

        config.pathCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.matchAny(subject.segments)) return nope()
        }

        config.nameCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.matchAny(subject.name)) return nope()
        }

        config.pathExclusions?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            // Check what the path should not contain
            if (exclusions.matchAny(subject.segments)) return nope()
        }

        config.pathRegexes?.takeIf { it.isNotEmpty() }?.let { regexes ->
            if (regexes.none { it.matches(subject.path) }) return nope()
        }

        var areaInfo: AreaInfo? = null
        var pfpSegments: List<String>? = null

        suspend fun areaLoader(): Boolean {
            if (areaInfo != null) return true
            areaInfo = fileForensics.identifyArea(subject)
            if (areaInfo == null) {
                log(TAG, WARN) { "Couldn't identify area for $subject" }
                return false
            }
            pfpSegments = areaInfo!!.prefixFreeSegments
            return true
        }

        config.areaTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            val loaded = areaLoader()
            val typeMatches = areaInfo?.type in types
            if (!loaded || !typeMatches) return nope()
        }

        // Now working on the prefix-free-path!
        // Check pfp startsWith/ancestorOf

        config.pfpExclusions?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!areaLoader()) return nope()
            if (criteria.matchAny(pfpSegments!!)) return nope()
        }

        config.pfpCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!areaLoader()) return nope()
            if (!criteria.matchAny(pfpSegments!!)) return nope()
        }

        return Result(
            subject,
            matches = true,
            areaInfo = areaInfo
        )
    }

    data class Config(
        val areaTypes: Set<DataArea.Type>? = null,
        val targetTypes: Set<TypeCriterium>? = null,
        val nameCriteria: Set<NameCriterium>? = null,
        val pathCriteria: Set<SegmentCriterium>? = null,
        val pathExclusions: Set<SegmentCriterium>? = null,
        val pathRegexes: Set<Regex>? = null,
        val pfpCriteria: Set<SegmentCriterium>? = null,
        val pfpExclusions: Set<SegmentCriterium>? = null,
        val maximumSize: Long? = null,
        val minimumSize: Long? = null,
        val maximumAge: Duration? = null,
        val minimumAge: Duration? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(config: Config): SystemCrawlerSieve
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "SystemCrawler", "BaseSieve")
    }

}