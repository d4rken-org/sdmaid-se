package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.restrictedCharset
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.files.Segments
import eu.darken.sdmse.common.files.matches
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

open class NestedPackageMatcher(
    val areaType: DataArea.Type,
    val baseSegments: List<String>,
    val badMatches: Set<String>
) : MarkerSource {

    private val dynamicMarkers: MutableCollection<Marker> = HashSet()
    private val markerMapByPkg: MutableMap<Pkg.Id, MutableCollection<Marker>> = HashMap()

    private val ignoreCase: Boolean = areaType.restrictedCharset

    init {
        require(baseSegments.isNotEmpty()) { "baseSegments is empty" }
        require(baseSegments != listOf("")) { "baseSegments is initialised with root only" }

        dynamicMarkers.add(object : Marker {
            override val areaType: DataArea.Type = this@NestedPackageMatcher.areaType
            override val flags: Set<Marker.Flag> = emptySet()

            override fun match(otherAreaType: DataArea.Type, otherSegments: List<String>): Marker.Match? {
                if (otherSegments.size != baseSegments.size + 1) return null
                for (i in baseSegments.indices) {
                    if (!otherSegments[i].equals(baseSegments[i], ignoreCase)) {
                        return null
                    }
                }
                if (this@NestedPackageMatcher.badMatches.contains(otherSegments[otherSegments.size - 1])) return null

                return when {
                    !otherSegments.last().contains(".") -> null
                    else -> Marker.Match(setOf(otherSegments.last().toPkgId()))
                }
            }

            override val segments: List<String> = baseSegments

            override val isDirectMatch: Boolean = false
        })
    }

    override suspend fun getMarkerForLocation(location: DataArea.Type): Collection<Marker> {
        return if (location === this.areaType) dynamicMarkers else emptyList()
    }

    override suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: List<String>): Collection<Marker.Match> {
        return dynamicMarkers.mapNotNull { it.match(areaType, prefixFreeBasePath) }
    }

    override suspend fun getMarkerForPkg(pkgId: Pkg.Id): Collection<Marker> {
        var markers = markerMapByPkg[pkgId]
        if (markers == null) {
            markers = HashSet()
            markers.add(PackageMarker(areaType, baseSegments.plus(pkgId.name), pkgId))
            markerMapByPkg[pkgId] = markers
        }
        return markers
    }

    private class PackageMarker constructor(
        override val areaType: DataArea.Type,
        override val segments: Segments,
        val pkgId: Pkg.Id,
    ) : Marker {
        private val ignoreCase: Boolean = areaType.restrictedCharset

        override val flags: Set<Marker.Flag> = emptySet()

        override fun match(otherAreaType: DataArea.Type, otherSegments: Segments): Marker.Match? {
            if (this.areaType !== otherAreaType) return null

            return if (otherSegments.matches(this.segments, ignoreCase)) Marker.Match(setOf(pkgId)) else null
        }

        override val isDirectMatch: Boolean = true
    }
}