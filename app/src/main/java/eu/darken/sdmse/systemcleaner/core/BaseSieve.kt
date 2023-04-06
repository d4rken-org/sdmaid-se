package eu.darken.sdmse.systemcleaner.core

import androidx.annotation.Keep
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.*
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.forensics.identifyArea

class BaseSieve @AssistedInject constructor(
    @Assisted private val config: Config,
    private val fileForensics: FileForensics,
) {

    @Keep
    enum class TargetType {
        FILE,
        DIRECTORY,
        ;
    }

    data class Result(
        val matches: Boolean,
        val areaInfo: AreaInfo? = null,
    )

    suspend fun match(subject: APathLookup<*>): Result {
        // Directory or file?
        config.targetType?.let {
            if ((it == TargetType.DIRECTORY && !subject.isDirectory || it == TargetType.FILE && !subject.isFile)) {
                return Result(matches = false)
            }
        }

        config.isEmpty?.let {
            // Empty or not ?
            if (it && subject.size > 0 || !it && subject.size == 0L) return Result(matches = false)
        }

        config.maximumSize?.let {
            // Is our subject too large?
            if (subject.size > it) return Result(matches = false)
        }

        config.minimumSize?.let {
            // Maybe it's too small
            if (subject.size < it) return Result(matches = false)
        }

        config.maximumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.toEpochMilli() > it) return Result(matches = false)
        }

        config.minimumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.toEpochMilli() < it) return Result(matches = false)
        }

        config.pathContains
            ?.takeIf { it.isNotEmpty() }
            ?.let { pathContains ->
                // Path contains
                if (pathContains.none { subject.segments.containsSegments(it) }) return Result(matches = false)
            }

        config.namePrefixes
            ?.takeIf { it.isNotEmpty() }
            ?.let { inits ->
                // Check name starts with
                if (inits.none { subject.name.startsWith(it) }) return Result(matches = false)
            }

        config.nameSuffixes
            ?.takeIf { it.isNotEmpty() }
            ?.let { ends ->
                if (ends.none { subject.name.endsWith(it) }) return Result(matches = false)
            }

        config.exclusions
            ?.takeIf { it.isNotEmpty() }
            ?.let { exclusions ->
                // Check what the path should not contain
                val match = exclusions.any {
                    subject.segments.containsSegments(it.segments, allowPartial = it.allowPartial)
                }
                if (match) return Result(matches = false)
            }

        config.regexes
            ?.takeIf { it.isNotEmpty() }
            ?.let { regexes ->
                if (regexes.none { it.matches(subject.path) }) return Result(matches = false)
            }

        val areaInfo = fileForensics.identifyArea(subject)
        if (areaInfo == null) {
            log(TAG, WARN) { "Couldn't identify area for $subject" }
            return Result(matches = false)
        }
        val subjectSegments = areaInfo.prefixFreePath

        config.areaTypes
            ?.takeIf { it.isNotEmpty() }
            ?.let { types ->
                if (!types.contains(areaInfo.type)) return Result(matches = false)
            }

        config.pathAncestors
            ?.takeIf { it.isNotEmpty() }
            ?.let { basePaths ->
                // Check path starts with
                if (basePaths.none { it.isAncestorOf(subjectSegments) }) return Result(matches = false)
            }

        config.pathPrefixes
            ?.takeIf { it.isNotEmpty() }
            ?.let { basePaths ->
                // Like basepath, but allows for partial matches
                if (basePaths.none { subjectSegments.startsWith(it) }) return Result(matches = false)
            }

        return Result(
            matches = true,
            areaInfo = areaInfo
        )
    }

    data class Config(
        val maximumSize: Long? = null,
        val minimumSize: Long? = null,
        val maximumAge: Long? = null,
        val minimumAge: Long? = null,
        val targetType: TargetType? = null,
        val isEmpty: Boolean? = null,
        val areaTypes: Set<DataArea.Type>? = null,
        val pathAncestors: Set<Segments>? = null,
        val pathPrefixes: Set<Segments>? = null,
        val pathContains: Set<Segments>? = null,
        val regexes: Set<Regex>? = null,
        val exclusions: Set<Exclusion>? = null,
        val namePrefixes: Set<String>? = null,
        val nameSuffixes: Set<String>? = null,
    )

    data class Exclusion(
        val segments: Segments,
        val allowPartial: Boolean = true,
    )

    @AssistedFactory
    interface Factory {
        fun create(config: Config): BaseSieve
    }

    companion object {
        private val TAG = logTag("SystemCleaner", "SystemCrawler", "BaseSieve")
    }
}