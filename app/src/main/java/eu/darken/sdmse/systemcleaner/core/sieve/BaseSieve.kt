package eu.darken.sdmse.systemcleaner.core.sieve

import androidx.annotation.Keep
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.containsSegments
import eu.darken.sdmse.common.files.endsWith
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.files.isFile
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.files.startsWith
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea
import java.time.Duration

class BaseSieve @AssistedInject constructor(
    @Assisted val config: Config,
    private val fileForensics: FileForensics,
) {

    @Keep
    enum class TargetType {
        FILE,
        DIRECTORY,
        ;
    }

    data class Result(
        val item: APathLookup<*>,
        val matches: Boolean,
        val areaInfo: AreaInfo? = null,
    )

    suspend fun match(subject: APathLookup<*>): Result {
        val nope = { Result(subject, matches = false) }

        // Directory or file?
        config.targetTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            if (subject.isFile && !types.contains(TargetType.FILE)) {
                return nope()
            } else if (subject.isDirectory && !types.contains(TargetType.DIRECTORY)) {
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
            if (!criteria.match(subject.segments)) return nope()
        }

        config.nameCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.match(subject.name)) return nope()
        }

        config.pathExclusions?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            // Check what the path should not contain
            if (exclusions.match(subject.segments)) return nope()
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
            if (criteria.match(pfpSegments!!)) return nope()
        }

        config.pfpCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!areaLoader()) return nope()
            if (!criteria.match(pfpSegments!!)) return nope()
        }

        return Result(
            subject,
            matches = true,
            areaInfo = areaInfo
        )
    }

    private fun Collection<NameCriterium>.match(target: String): Boolean = any { crit ->
        when (crit.mode) {
            is NameCriterium.Mode.Start -> target.startsWith(
                crit.name,
                ignoreCase = crit.mode.ignoreCase
            )

            is NameCriterium.Mode.End -> target.endsWith(
                crit.name,
                ignoreCase = crit.mode.ignoreCase
            )

            is NameCriterium.Mode.Contain -> target.contains(
                crit.name,
                ignoreCase = crit.mode.ignoreCase
            )

            is NameCriterium.Mode.Equal -> target.equals(
                crit.name,
                ignoreCase = crit.mode.ignoreCase
            )
        }
    }

    private fun Collection<SegmentCriterium>.match(target: Segments): Boolean = any { crit ->
        when (crit.mode) {
            is SegmentCriterium.Mode.Ancestor -> crit.segments.isAncestorOf(
                target,
                ignoreCase = crit.mode.ignoreCase,
            )

            is SegmentCriterium.Mode.Start -> target.startsWith(
                crit.segments,
                ignoreCase = crit.mode.ignoreCase,
                allowPartial = crit.mode.allowPartial,
            )

            is SegmentCriterium.Mode.End -> target.endsWith(
                crit.segments,
                ignoreCase = crit.mode.ignoreCase,
                allowPartial = crit.mode.allowPartial,
            )

            is SegmentCriterium.Mode.Contain -> target.containsSegments(
                crit.segments,
                allowPartial = crit.mode.allowPartial,
                ignoreCase = crit.mode.ignoreCase
            )

            is SegmentCriterium.Mode.Equal -> target.matches(
                crit.segments,
                ignoreCase = crit.mode.ignoreCase
            )
        }
    }

    data class Config(
        val areaTypes: Set<DataArea.Type>? = null,
        val targetTypes: Set<TargetType>? = null,
        val pathCriteria: Set<SegmentCriterium>? = null,
        val pfpCriteria: Set<SegmentCriterium>? = null,
        val nameCriteria: Set<NameCriterium>? = null,
        val pathExclusions: Set<SegmentCriterium>? = null,
        val pfpExclusions: Set<SegmentCriterium>? = null,
        val pathRegexes: Set<Regex>? = null,
        val maximumSize: Long? = null,
        val minimumSize: Long? = null,
        val maximumAge: Duration? = null,
        val minimumAge: Duration? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(config: Config): BaseSieve
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "SystemCrawler", "BaseSieve")
    }

}