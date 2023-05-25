package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.time.Instant

fun BaseFilterTest.pos(pkg: String, areaType: DataArea.Type, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.POSITIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        prefixFreePaths = setOf(segments.map { it.split(File.separatorChar) }.flatten())
    ).let { addCandidate(it) }
}

fun BaseFilterTest.neg(pkg: String, areaType: DataArea.Type, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.NEGATIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        prefixFreePaths = setOf(segments.map { it.split(File.separatorChar) }.flatten())
    ).let { addCandidate(it) }
}

fun BaseFilterTest.pos(pkg: String, areaType: DataArea.Type, lastModified: Instant, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.POSITIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        prefixFreePaths = setOf(segments.map { it.split(File.separatorChar) }.flatten()),
        lastModified = lastModified,
    ).let { addCandidate(it) }
}

fun BaseFilterTest.neg(pkg: String, areaType: DataArea.Type, lastModified: Instant, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.NEGATIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        prefixFreePaths = setOf(segments.map { it.split(File.separatorChar) }.flatten()),
        lastModified = lastModified,
    ).let { addCandidate(it) }
}