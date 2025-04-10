package eu.darken.sdmse.appcleaner.core.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.time.Instant

fun BaseFilterTest.pos(pkgs: Set<String>, areaType: DataArea.Type, vararg segments: String) {
    pos(pkgs, setOf(areaType), *segments)
}

fun BaseFilterTest.pos(pkg: String, areaType: DataArea.Type, vararg segments: String) {
    pos(setOf(pkg), setOf(areaType), *segments)
}

fun BaseFilterTest.pos(pkg: String, areaTypes: Set<DataArea.Type>, vararg segments: String) {
    pos(setOf(pkg), areaTypes, *segments)
}

fun BaseFilterTest.pos(pkgs: Set<String>, areaTypes: Set<DataArea.Type>, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.POSITIVE,
        pkgs = pkgs.map { it.toPkgId() },
        areaTypes = areaTypes,
        pfpSegs = segments.map { it.split(File.separatorChar) }.flatten()
    ).let { addCandidate(it) }
}

fun BaseFilterTest.neg(pkgs: Set<String>, areaType: DataArea.Type, vararg segments: String) {
    neg(pkgs, setOf(areaType), *segments)
}

fun BaseFilterTest.neg(pkg: String, areaType: DataArea.Type, vararg segments: String) {
    neg(setOf(pkg), setOf(areaType), *segments)
}

fun BaseFilterTest.neg(pkg: String, areaTypes: Set<DataArea.Type>, vararg segments: String) {
    neg(setOf(pkg), areaTypes, *segments)
}

fun BaseFilterTest.neg(pkgs: Set<String>, areaTypes: Set<DataArea.Type>, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.NEGATIVE,
        pkgs = pkgs.map { it.toPkgId() },
        areaTypes = areaTypes,
        pfpSegs = segments.map { it.split(File.separatorChar) }.flatten()
    ).let { addCandidate(it) }
}

fun BaseFilterTest.pos(pkg: String, areaType: DataArea.Type, lastModified: Instant, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.POSITIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        pfpSegs = segments.map { it.split(File.separatorChar) }.flatten(),
        lastModified = lastModified,
    ).let { addCandidate(it) }
}

fun BaseFilterTest.neg(pkg: String, areaType: DataArea.Type, lastModified: Instant, vararg segments: String) {
    BaseFilterTest.Candidate(
        matchType = BaseFilterTest.Candidate.Type.NEGATIVE,
        pkgs = setOf(pkg.toPkgId()),
        areaTypes = setOf(areaType),
        pfpSegs = segments.map { it.split(File.separatorChar) }.flatten(),
        lastModified = lastModified,
    ).let { addCandidate(it) }
}