package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.restrictedCharset
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.io.File

open class NestedPackageMatcher(
    val areaType: DataArea.Type,
    val baseDir: String,
    val badMatches: Set<String>
) : MarkerSource {

    private val dynamicMarkers: MutableCollection<Marker> = HashSet()
    private val markerMapByPkg: MutableMap<Pkg.Id, MutableCollection<Marker>> = HashMap()

    val baseDirsSplit = baseDir.split("/").toTypedArray()
    private val ignoreCase: Boolean = areaType.restrictedCharset

    init {
        require(baseDir.isNotEmpty()) { "BaseDir is empty" }

        dynamicMarkers.add(object : Marker {
            override val areaType: DataArea.Type = this@NestedPackageMatcher.areaType
            override val flags: Set<Marker.Flag> = emptySet()

            override fun match(areaType: DataArea.Type, prefixFree: String): Marker.Match? {
                val split = prefixFree.split("/")
                if (split.size != baseDirsSplit.size + 1) return null
                for (i in baseDirsSplit.indices) {
                    if (!split[i].equals(baseDirsSplit[i], ignoreCase)) {
                        return null
                    }
                }
                if (this@NestedPackageMatcher.badMatches.contains(split[split.size - 1])) return null

                return if (!split.last().contains(".")) null else Marker.Match(setOf(split.last().toPkgId()))
            }

            override val prefixFreeBasePath: String = baseDir

            override val isPrefixFreeBasePathDirect: Boolean = false
        })
    }

    override suspend fun getMarkerForLocation(location: DataArea.Type): Collection<Marker> {
        return if (location === this.areaType) dynamicMarkers else emptyList()
    }

    override suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: String): Collection<Marker.Match> {
        return dynamicMarkers.mapNotNull { it.match(areaType, prefixFreeBasePath) }
    }

    override suspend fun getMarkerForPkg(pkgId: Pkg.Id): Collection<Marker> {
        var markers = markerMapByPkg[pkgId]
        if (markers == null) {
            markers = HashSet()
            markers.add(PackageMarker(areaType, "$baseDir${File.separatorChar}${pkgId.name}", pkgId))
            markerMapByPkg[pkgId] = markers
        }
        return markers
    }

    private class PackageMarker constructor(
        override val areaType: DataArea.Type,
        override val prefixFreeBasePath: String,
        val pkgId: Pkg.Id,
    ) : Marker {
        private val ignoreCase: Boolean = areaType.restrictedCharset

        override val flags: Set<Marker.Flag> = emptySet()

        override fun match(areaType: DataArea.Type, prefixFree: String): Marker.Match? {
            if (this.areaType !== areaType) return null

            return if (prefixFree.equals(prefixFreeBasePath, ignoreCase)) {
                Marker.Match(setOf(pkgId))
            } else null
        }

        override val isPrefixFreeBasePathDirect: Boolean = true
    }
}