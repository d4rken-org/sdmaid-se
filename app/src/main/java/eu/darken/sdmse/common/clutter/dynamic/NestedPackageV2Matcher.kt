package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.files.isAncestorOf
import eu.darken.sdmse.common.files.joinSegments
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File

open class NestedPackageV2Matcher(
    val dataAreaType: DataArea.Type,
    val basePath: List<String>,
    val goodRegexes: Set<Regex>,
    val badRegexes: Set<Regex>,
    val flags: Set<Marker.Flag>,
    val converter: Converter
) : MarkerSource {
    private val dynamicMarkers = mutableSetOf<Marker>()
    private val markerMapByPkg = mutableMapOf<Pkg.Id, Set<Marker>?>()

    val ignoreCase: Boolean = dataAreaType.isCaseInsensitive

    interface Converter {
        fun onConvertMatchToPackageNames(match: MatchResult): Set<Pkg.Id>
        fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>>

        class PackagePathConverter : Converter {
            override fun onConvertMatchToPackageNames(match: MatchResult): Set<Pkg.Id> {
                return setOf(match.groupValues[1].replace(File.separatorChar, '.').toPkgId())
            }

            override fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>> {
                return setOf(pkgId.name.split('.'))
            }
        }
    }

    init {
        require(basePath.isNotEmpty()) { "Prefix is empty" }
        require(!basePath.any { it.contains(File.separator) }) { "Prefix should not contain " + File.separatorChar }
        require(goodRegexes.isNotEmpty()) { "Good matches is empty" }
        require(goodRegexes.iterator().next().pattern.isNotEmpty()) { "Empty patterns are not allowed" }

        dynamicMarkers.add(object : Marker {
            override val flags: Set<Marker.Flag>
                get() = this@NestedPackageV2Matcher.flags

            override val areaType: DataArea.Type
                get() = this@NestedPackageV2Matcher.dataAreaType

            override fun match(otherAreaType: DataArea.Type, otherSegments: List<String>): Marker.Match? {
                if (!this.segments.isAncestorOf(otherSegments, ignoreCase)) {
                    return null
                }
                val joinedSegments = otherSegments.joinSegments()

                val goodRegex = goodRegexes.firstNotNullOfOrNull {
                    it.matchEntire(joinedSegments)
                } ?: return null

                if (badRegexes.any { it.matches(joinedSegments) }) {
                    return null
                }

                val pkgNames = this@NestedPackageV2Matcher.converter.onConvertMatchToPackageNames(goodRegex)
                return Marker.Match(pkgNames, this@NestedPackageV2Matcher.flags)
            }

            override val segments: List<String> = this@NestedPackageV2Matcher.basePath

            override val isDirectMatch: Boolean = false
        })
    }

    override suspend fun getMarkerForLocation(areaType: DataArea.Type): Collection<Marker> {
        return if (areaType === dataAreaType) dynamicMarkers else emptyList()
    }

    override suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: List<String>): Collection<Marker.Match> {
        return dynamicMarkers.mapNotNull { it.match(areaType, prefixFreeBasePath) }
    }

    override suspend fun getMarkerForPkg(pkgId: Pkg.Id): Collection<Marker> {
        val fromCache = markerMapByPkg[pkgId]
        if (fromCache != null) return fromCache
        return converter.onConvertPackageNameToPaths(pkgId)
            .map { PackageMarker(dataAreaType, basePath.plus(it), pkgId, emptySet()) }
            .toSet()
            .also { markerMapByPkg[pkgId] = it }
    }

    private class PackageMarker constructor(
        override val areaType: DataArea.Type,
        override val segments: List<String>,
        val pkgId: Pkg.Id,
        override val flags: Set<Marker.Flag>
    ) : Marker {

        private val ignoreCase: Boolean = areaType.isCaseInsensitive

        override val isDirectMatch: Boolean = true

        override fun match(otherAreaType: DataArea.Type, otherSegments: List<String>): Marker.Match? {
            if (this.areaType !== otherAreaType) return null

            return if (otherSegments.matches(this.segments, ignoreCase)) Marker.Match(setOf(pkgId), flags) else null
        }
    }
}