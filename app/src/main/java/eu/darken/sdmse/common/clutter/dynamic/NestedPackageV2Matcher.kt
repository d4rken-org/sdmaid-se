package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.files.core.isAncestorOf
import eu.darken.sdmse.common.files.core.matches
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

open class NestedPackageV2Matcher(
    val dataAreaType: DataArea.Type,
    val basePath: List<String>,
    val goodMatches: Set<Pattern>,
    val badMatches: Set<Pattern>,
    val flags: Set<Marker.Flag>,
    val converter: Converter
) : MarkerSource {
    private val dynamicMarkers = mutableSetOf<Marker>()
    private val markerMapByPkg = mutableMapOf<Pkg.Id, Set<Marker>?>()

    val ignoreCase: Boolean = dataAreaType.isCaseInsensitive

    interface Converter {
        fun onConvertMatchToPackageNames(matcher: Matcher): Set<Pkg.Id>
        fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>>

        class PackagePathConverter : Converter {
            override fun onConvertMatchToPackageNames(matcher: Matcher): Set<Pkg.Id> {
                return setOf(matcher.group(1).replace(File.separatorChar, '.').toPkgId())
            }

            override fun onConvertPackageNameToPaths(pkgId: Pkg.Id): Set<List<String>> {
                return setOf(pkgId.name.split('.'))
            }
        }
    }

    init {
        require(basePath.isNotEmpty()) { "Prefix is empty" }
        require(!basePath.any { it.contains(File.separator) }) { "Prefix should not contain " + File.separatorChar }
        require(goodMatches.isNotEmpty()) { "Good matches is empty" }
        require(goodMatches.iterator().next().pattern().isNotEmpty()) { "Empty patterns are not allowed" }

        dynamicMarkers.add(object : Marker {
            override val flags: Set<Marker.Flag>
                get() = this@NestedPackageV2Matcher.flags

            override val areaType: DataArea.Type
                get() = this@NestedPackageV2Matcher.dataAreaType

            override fun match(otherAreaType: DataArea.Type, otherSegments: List<String>): Marker.Match? {
                if (!this.segments.isAncestorOf(otherSegments, ignoreCase)) {
                    return null
                }
                val joinedSegments = otherSegments.joinToString("/")
                var goodMatcher: Matcher? = null
                for (p in goodMatches) {
                    val matcher = p.matcher(joinedSegments)
                    if (matcher.matches()) {
                        goodMatcher = matcher
                        break
                    }
                }
                if (goodMatcher == null) return null
                for (p in badMatches) {
                    if (p.matcher(joinedSegments).matches()) {
                        return null
                    }
                }
                val pkgNames = this@NestedPackageV2Matcher.converter.onConvertMatchToPackageNames(goodMatcher)
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

            return if (otherSegments.matches(this.segments, ignoreCase)) Marker.Match(setOf(pkgId)) else null
        }
    }
}