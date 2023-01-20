package eu.darken.sdmse.appcleaner.core.forensics.sieves.dynamic

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.files.core.Segments
import eu.darken.sdmse.common.files.core.containsSegments
import eu.darken.sdmse.common.files.core.joinSegments
import eu.darken.sdmse.common.files.core.startsWith
import eu.darken.sdmse.common.pkgs.Pkg


class DynamicSieve @AssistedInject constructor(
    @Assisted private val configs: Set<MatchConfig>,
) {

    init {
        if (configs.isEmpty()) throw IllegalArgumentException("Empty match configs")
    }

    data class MatchConfig(
        val pkgNames: Set<Pkg.Id>? = null,
        val areaTypes: Set<DataArea.Type>? = null,
        val contains: Set<Segments>? = null,
        val startsWith: Set<Segments>? = null,
        val patterns: Set<String>? = null,
        val exclusions: Set<Segments>? = null,
    ) {
        init {
            if (contains.isNullOrEmpty() && startsWith.isNullOrEmpty() && patterns.isNullOrEmpty()) {
                throw IllegalStateException("Underdefined match config")
            }
        }
    }

    fun matches(
        pkgId: Pkg.Id,
        areaType: DataArea.Type,
        target: Segments,
    ): Boolean = configs.any { it.match(pkgId, areaType, target) }

    private fun MatchConfig.match(
        pkgId: Pkg.Id,
        areaType: DataArea.Type,
        target: Segments,
    ): Boolean {
        if (!pkgNames.isNullOrEmpty() && !pkgNames.contains(pkgId)) {
            return false
        }

        if (!areaTypes.isNullOrEmpty() && !areaTypes.contains(areaType)) {
            return false
        }


        val ignoreCase = areaType.isCaseInsensitive

        exclusions?.takeIf { it.isNotEmpty() }?.let { excls ->
            val excluded = excls.any {
                target.containsSegments(it, ignoreCase = ignoreCase, allowPartial = true)
            }
            if (excluded) return@match false
        }

        val startsWithCondition = startsWith
            ?.takeIf { it.isNotEmpty() }
            ?.let { starters ->
                starters.any { target.startsWith(it, ignoreCase = ignoreCase) }
            }
            ?: true

        val containsCondition = contains
            ?.takeIf { it.isNotEmpty() }
            ?.let { contains ->
                contains.any {
                    target.containsSegments(
                        it,
                        ignoreCase = ignoreCase,
                        allowPartial = true
                    )
                }
            }
            ?: true

        val regexCondition = patterns
            ?.takeIf { it.isNotEmpty() }
            ?.let { rawPatterns ->
                val segsAsPath = target.joinSegments()
                rawPatterns.any { rawPattern ->
                    val regex =
                        if (ignoreCase) Regex(rawPattern, RegexOption.IGNORE_CASE) else Regex(rawPattern)
                    regex.matches(segsAsPath)
                }
            }
            ?: true

        return startsWithCondition && containsCondition && regexCondition
    }

    @AssistedFactory
    interface Factory {
        fun create(configs: Set<MatchConfig>): DynamicSieve
    }
}