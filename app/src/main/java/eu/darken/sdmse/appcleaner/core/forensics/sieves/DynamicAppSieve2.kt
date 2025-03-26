package eu.darken.sdmse.appcleaner.core.forensics.sieves

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.sieve.Criterium
import eu.darken.sdmse.common.sieve.FileSieve


class DynamicAppSieve2 @AssistedInject constructor(
    @Assisted private val configs: Set<MatchConfig>,
) : FileSieve {

    init {
        if (configs.isEmpty()) throw IllegalArgumentException("Empty match configs")
    }

    data class MatchConfig(
        val pkgNames: Set<Pkg.Id>? = null,
        val areaTypes: Set<DataArea.Type>? = null,
        val pfpCriteria: Set<Criterium>? = null,
        val pfpExclusions: Set<Criterium>? = null,
        val pfpRegexes: Set<Regex>? = null,
    ) {
        init {
            if (pfpCriteria.isNullOrEmpty() && pfpRegexes.isNullOrEmpty()) {
                throw IllegalStateException("Underdefined match config")
            }
        }
    }

    fun matches(
        pkgId: Pkg.Id,
        target: APathLookup<*>,
        areaType: DataArea.Type,
        pfpSegs: Segments,
    ): Boolean = configs.any {
        it.match(pkgId = pkgId, target = target, areaType = areaType, pfpSegs = pfpSegs)
    }

    private fun MatchConfig.match(
        pkgId: Pkg.Id,
        target: APathLookup<*>,
        areaType: DataArea.Type,
        pfpSegs: Segments,
    ): Boolean {
        areaTypes?.takeIf { it.isNotEmpty() }?.let { types ->
            if (!types.contains(areaType)) return false
        }

        pkgNames?.takeIf { it.isNotEmpty() }?.let { pkgs ->
            if (!pkgs.contains(pkgId)) return false
        }

        pfpExclusions?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            if (exclusions.match(pfpSegs)) return false
        }

        pfpCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.match(pfpSegs)) return false
        }

        pfpRegexes?.takeIf { it.isNotEmpty() }?.let { regexes ->
            val match = regexes.any { it.matches(pfpSegs.joinSegments()) }
            if (!match) return false
        }

        return true
    }

    @AssistedFactory
    interface Factory {
        fun create(configs: Set<MatchConfig>): DynamicAppSieve2
    }
}