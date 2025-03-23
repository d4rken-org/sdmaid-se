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
        // Directory or file?
        config.targetTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            if (subject.isFile && !types.contains(TargetType.FILE)) {
                return Result(subject, matches = false)
            } else if (subject.isDirectory && !types.contains(TargetType.DIRECTORY)) {
                return Result(subject, matches = false)
            }
        }

        config.maximumSize?.let {
            // Is our subject too large?
            if (subject.size > it) return Result(subject, matches = false)
        }

        config.minimumSize?.let {
            // Maybe it's too small
            if (subject.size < it) return Result(subject, matches = false)
        }

        config.maximumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.toEpochMilli() > it.toMillis()) {
                return Result(subject, matches = false)
            }
        }

        config.minimumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.toEpochMilli() < it.toMillis()) {
                return Result(subject, matches = false)
            }
        }

        config.pathCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            val hasMatch = criteria.match(subject.segments)
            if (!hasMatch) return Result(subject, matches = false)
        }

        config.nameCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            val hasMatch = criteria.any { crit ->
                when (crit.mode) {
                    is NameCriterium.Mode.Start -> subject.name.startsWith(
                        crit.name,
                        ignoreCase = crit.mode.ignoreCase
                    )

                    is NameCriterium.Mode.End -> subject.name.endsWith(
                        crit.name,
                        ignoreCase = crit.mode.ignoreCase
                    )

                    is NameCriterium.Mode.Contain -> subject.name.contains(
                        crit.name,
                        ignoreCase = crit.mode.ignoreCase
                    )

                    is NameCriterium.Mode.Equal -> subject.name.equals(
                        crit.name,
                        ignoreCase = crit.mode.ignoreCase
                    )

                }
            }

            if (!hasMatch) return Result(subject, matches = false)
        }

        config.pathExclusions?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            // Check what the path should not contain
            val isExcluded = exclusions.match(subject.segments)
            if (isExcluded) return Result(subject, matches = false)
        }

        config.pathRegexes?.takeIf { it.isNotEmpty() }?.let { regexes ->
            if (regexes.none { it.matches(subject.path) }) return Result(subject, matches = false)
        }

        val areaInfo = fileForensics.identifyArea(subject)
        if (areaInfo == null) {
            log(TAG, WARN) { "Couldn't identify area for $subject" }
            return Result(subject, matches = false)
        }
        val pfpSegments = areaInfo.prefixFreeSegments

        config.areaTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            val matchesArea = types.contains(areaInfo.type)
            if (!matchesArea) return Result(subject, matches = false)
        }

        // Now working on the prefix-free-path!
        // Check pfp startsWith/ancestorOf

        config.pfpExclusions?.takeIf { it.isNotEmpty() }?.let { criteria ->
            val isExcluded = criteria.match(pfpSegments)
            if (isExcluded) return Result(subject, matches = false)
        }

        config.pfpCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            val hasMatch = criteria.match(pfpSegments)
            if (!hasMatch) return Result(subject, matches = false)
        }

        return Result(
            subject,
            matches = true,
            areaInfo = areaInfo
        )
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