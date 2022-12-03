package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.storageareas.StorageArea
import eu.darken.sdmse.common.storageareas.restrictedCharset
import java.io.File

open class NestedPackageMatcher(
    val areaType: StorageArea.Type,
    val baseDir: String,
    val badMatches: Set<String>
) : MarkerSource {

    private val dynamicMarkers: MutableCollection<Marker> = HashSet()
    private val markerMapByPkg: MutableMap<String, MutableCollection<Marker>> = HashMap()

    val baseDirsSplit = baseDir.split("/").toTypedArray()
    private val ignoreCase: Boolean = areaType.restrictedCharset

    init {
        require(baseDir.isNotEmpty()) { "BaseDir is empty" }

        dynamicMarkers.add(object : Marker {
            override val areaType: StorageArea.Type = this@NestedPackageMatcher.areaType
            override val flags: Set<Marker.Flag> = emptySet()

            override fun match(location: StorageArea.Type, prefixFree: String): Marker.Match? {
                val split = prefixFree.split("/")
                if (split.size != baseDirsSplit.size + 1) return null
                for (i in baseDirsSplit.indices) {
                    if (!split[i].equals(baseDirsSplit[i], ignoreCase)) {
                        return null
                    }
                }
                if (this@NestedPackageMatcher.badMatches.contains(split[split.size - 1])) return null

                return if (!split.last().contains(".")) null else Marker.Match(setOf(split.last()))
            }

            override val prefixFreeBasePath: String = baseDir

            override val isPrefixFreeBasePathDirect: Boolean = false
        })
    }

    override suspend fun getMarkerForLocation(location: StorageArea.Type): Collection<Marker> {
        return if (location === this.areaType) dynamicMarkers else emptyList()
    }

    override suspend fun match(areaType: StorageArea.Type, prefixFreeBasePath: String): Collection<Marker.Match> {
        return dynamicMarkers.mapNotNull { it.match(areaType, prefixFreeBasePath) }
    }

    override suspend fun getMarkerForPackageName(packageName: String): Collection<Marker> {
        var markers = markerMapByPkg[packageName]
        if (markers == null) {
            markers = HashSet()
            markers.add(PackageMarker(areaType, baseDir + File.separatorChar + packageName, packageName))
            markerMapByPkg[packageName] = markers
        }
        return markers
    }

    private class PackageMarker constructor(
        override val areaType: StorageArea.Type,
        override val prefixFreeBasePath: String,
        val packageName: String
    ) : Marker {
        private val ignoreCase: Boolean = areaType.restrictedCharset

        override val flags: Set<Marker.Flag> = emptySet()

        override fun match(areaType: StorageArea.Type, prefixFree: String): Marker.Match? {
            if (this.areaType !== areaType) return null

            return if (prefixFree.equals(prefixFreeBasePath, ignoreCase)) {
                Marker.Match(setOf(packageName))
            } else null
        }

        override val isPrefixFreeBasePathDirect: Boolean = true
    }
}