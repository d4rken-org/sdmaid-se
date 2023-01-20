package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId


data class LegacyCandidate(
    val type: Type,
    val pkgs: Collection<Pkg.Id>? = null,
    val areaTypes: Collection<DataArea.Type>? = null,
    val prefixFreePath: String? = null
) {
    enum class Type {
        POSITIVE,
        NEGATIVE,
        ;
    }
}

fun BaseFilterTest.addCandidate(legcan: LegacyCandidate) {
    val newCan = BaseFilterTest.Candidate(
        matchType = when (legcan.type) {
            LegacyCandidate.Type.POSITIVE -> BaseFilterTest.Candidate.Type.POSITIVE
            LegacyCandidate.Type.NEGATIVE -> BaseFilterTest.Candidate.Type.NEGATIVE
        },
        pkgs = legcan.pkgs ?: emptySet(),
        areaTypes = legcan.areaTypes ?: emptySet(),
        prefixFreePaths = setOf(legcan.prefixFreePath!!.split("/")),
    )
    addCandidate(newCan)
}

fun pos() = LegacyCandidate(
    type = LegacyCandidate.Type.POSITIVE,
)

fun neg() = LegacyCandidate(
    type = LegacyCandidate.Type.NEGATIVE,
)

fun LegacyCandidate.pkgs(vararg pkgs: Pkg.Id) = this.copy(
    pkgs = pkgs.toSet(),
)

fun LegacyCandidate.pkgs(vararg pkgs: String) = this.copy(
    pkgs = pkgs.map { it.toPkgId() }.toSet(),
)

fun LegacyCandidate.locs(vararg areaTypes: DataArea.Type) = this.copy(
    areaTypes = areaTypes.toSet()
)

fun LegacyCandidate.prefixFree(prefixFree: String) = this.copy(
    prefixFreePath = prefixFree,
)

fun LegacyCandidate.build() = this