package eu.darken.sdmse.appcleaner.core.forensics.sieves

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.sieve.CriteriaOperator
import eu.darken.sdmse.common.sieve.FileSieve
import eu.darken.sdmse.common.sieve.NameCriterium


class DynamicAppSieve2 @AssistedInject constructor(
    @Assisted private val configs: Set<MatchConfig>,
) : FileSieve {

    init {
        if (configs.isEmpty()) throw IllegalArgumentException("Empty match configs")
    }

    data class MatchConfig(
        val pkgNames: Set<Pkg.Id>? = null,
        val areaTypes: Set<DataArea.Type>? = null,
        val nameCriteria: Set<NameCriterium>? = null,
        val nameExclusion: Set<NameCriterium>? = null,
        val pfpCriteria: Set<CriteriaOperator>? = null,
        val pfpExclusions: Set<CriteriaOperator>? = null,
        val pfpRegexes: Set<Regex>? = null,
    ) {
        init {
            if (nameCriteria.isNullOrEmpty() && pfpCriteria.isNullOrEmpty() && pfpRegexes.isNullOrEmpty()) {
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

        nameCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.matchAny(target.name)) return false
        }

        nameExclusion?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            if (exclusions.matchAny(target.name)) return false
        }

        pfpCriteria?.takeIf { it.isNotEmpty() }?.let { criteria ->
            if (!criteria.match(pfpSegs)) return false
        }

        pfpExclusions?.takeIf { it.isNotEmpty() }?.let { exclusions ->
            if (exclusions.match(pfpSegs)) return false
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