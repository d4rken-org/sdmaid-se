package eu.darken.sdmse.systemcleaner.core

import androidx.annotation.Keep
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.forensics.FileForensics
import java.util.regex.Pattern

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

    suspend fun match(subject: APathLookup<*>): Boolean {
        // Directory or file?
        config.targetType?.let {
            if ((it == TargetType.DIRECTORY && !subject.isDirectory || it == TargetType.FILE && !subject.isFile)) {
                return false
            }
        }

        config.isEmpty?.let {
            // Empty or not ?
            if (it && subject.size > 0 || !it && subject.size == 0L) return false
        }

        config.maximumSize?.let {
            // Is our subject too large?
            if (subject.size > it) return false
        }

        config.minimumSize?.let {
            // Maybe it's too small
            if (subject.size < it) return false
        }

        config.maximumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.time > it) return false
        }

        config.minimumAge?.let {
            if (System.currentTimeMillis() - subject.modifiedAt.time < it) return false
        }

        config.possibleBasePathes
            ?.takeIf { it.isNotEmpty() }
            ?.let { basePaths ->
                // Check path starts with
                if (basePaths.none { subject.path.startsWith(it) }) return false
            }

        config.possiblePathContains
            ?.takeIf { it.isNotEmpty() }
            ?.let { pathContains ->
                // Path contains
                if (pathContains.none { subject.path.contains(it) }) return false
            }

        config.possibleNameInits
            ?.takeIf { it.isNotEmpty() }
            ?.let { inits ->
                // Check name starts with
                if (inits.none { subject.name.startsWith(it) }) return false
            }

        config.possibleNameEndings
            ?.takeIf { it.isNotEmpty() }
            ?.let { ends ->
                if (ends.none { subject.name.endsWith(it) }) return false
            }

        config.exclusions
            ?.takeIf { it.isNotEmpty() }
            ?.let { exclusions ->
                // Check what the path should not contain
                if (exclusions.any { subject.path.contains(it) }) return false
            }

        config.regex
            ?.takeIf { it.isNotEmpty() }
            ?.let { regexes ->
                if (regexes.none { it.matcher(subject.path).matches() }) return false
            }

        config.locations
            ?.takeIf { it.isNotEmpty() }
            ?.let { types ->
                val areaInfo = fileForensics.identifyArea(subject)
                if (!types.contains(areaInfo.type)) return false
            }

        return true
    }

    data class Config(
        val maximumSize: Long? = null,
        val minimumSize: Long? = null,
        val maximumAge: Long? = null,
        val minimumAge: Long? = null,
        val targetType: TargetType? = null,
        val isEmpty: Boolean? = null,
        val locations: Set<DataArea.Type>? = null,
        val possibleBasePathes: Set<String>? = null,
        val possiblePathContains: Set<String>? = null,
        val possibleNameInits: Set<String>? = null,
        val possibleNameEndings: Set<String>? = null,
        val exclusions: Set<String>? = null,
        val regex: Set<Pattern>? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(config: Config): BaseSieve
    }
}