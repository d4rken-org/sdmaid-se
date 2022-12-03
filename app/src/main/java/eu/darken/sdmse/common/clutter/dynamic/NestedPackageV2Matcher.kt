package eu.darken.sdmse.common.clutter.dynamic

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.isCaseInsensitive
import eu.darken.sdmse.common.clutter.Marker
import eu.darken.sdmse.common.clutter.MarkerSource
import java.io.File
import java.util.regex.Matcher
import java.util.regex.Pattern

open class NestedPackageV2Matcher(
    val dataAreaType: DataArea.Type,
    val basePath: String,
    val goodMatches: Set<Pattern>,
    val badMatches: Set<Pattern>,
    val flags: Set<Marker.Flag>,
    val converter: Converter
) : MarkerSource {
    private val dynamicMarkers = mutableSetOf<Marker>()
    private val markerMapByPkg = mutableMapOf<String, Set<Marker>?>()

    val ignoreCase: Boolean = dataAreaType.isCaseInsensitive

    interface Converter {
        fun onConvertMatchToPackageNames(matcher: Matcher): Set<String>
        fun onConvertPackageNameToPaths(packageName: String): Set<String>
        class PackagePathConverter : Converter {
            override fun onConvertMatchToPackageNames(matcher: Matcher): Set<String> {
                return setOf(matcher.group(1).replace(File.separatorChar, '.'))
            }

            override fun onConvertPackageNameToPaths(packageName: String): Set<String> {
                return setOf(packageName.replace('.', File.separatorChar))
            }
        }
    }

    init {
        require(basePath.isNotEmpty()) { "Prefix is empty" }
        require(!basePath.endsWith(File.separator)) { "Prefix should not end with " + File.separatorChar }
        require(!goodMatches.isEmpty()) { "Good matches is empty" }
        require(goodMatches.iterator().next().pattern().isNotEmpty()) { "Empty patterns are not allowed" }

        dynamicMarkers.add(object : Marker {
            override val flags: Set<Marker.Flag>
                get() = this@NestedPackageV2Matcher.flags

            override val areaType: DataArea.Type
                get() = this@NestedPackageV2Matcher.dataAreaType

            override fun match(areaType: DataArea.Type, prefixFreePath: String): Marker.Match? {
                if (!prefixFreePath.startsWith(prefixFreeBasePath, ignoreCase)) {
                    return null
                }
                var goodMatcher: Matcher? = null
                for (p in goodMatches) {
                    val matcher = p.matcher(prefixFreePath)
                    if (matcher.matches()) {
                        goodMatcher = matcher
                        break
                    }
                }
                if (goodMatcher == null) return null
                for (p in badMatches) {
                    if (p.matcher(prefixFreePath).matches()) {
                        return null
                    }
                }
                val pkgNames = this@NestedPackageV2Matcher.converter.onConvertMatchToPackageNames(goodMatcher)
                return Marker.Match(pkgNames, this@NestedPackageV2Matcher.flags)
            }

            override val prefixFreeBasePath: String = this@NestedPackageV2Matcher.basePath

            override val isPrefixFreeBasePathDirect: Boolean = false
        })
    }

    override suspend fun getMarkerForLocation(areaType: DataArea.Type): Collection<Marker> {
        return if (areaType === dataAreaType) dynamicMarkers else emptyList()
    }

    override suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: String): Collection<Marker.Match> {
        return dynamicMarkers.mapNotNull { it.match(areaType, prefixFreeBasePath) }
    }

    override suspend fun getMarkerForPackageName(packageName: String): Collection<Marker> = markerMapByPkg[packageName]
        ?: converter.onConvertPackageNameToPaths(packageName)
            .map { PackageMarker(dataAreaType, basePath + File.separatorChar + it, packageName, emptySet()) }
            .toSet()
            .also { markerMapByPkg[packageName] = it }

    private class PackageMarker constructor(
        override val areaType: DataArea.Type,
        override val prefixFreeBasePath: String,
        val packageName: String,
        override val flags: Set<Marker.Flag>
    ) : Marker {

        private val ignoreCase: Boolean = areaType.isCaseInsensitive

        override val isPrefixFreeBasePathDirect: Boolean = true

        override fun match(areaType: DataArea.Type, prefixFree: String): Marker.Match? {
            if (this.areaType !== areaType) return null

            return if (prefixFree.equals(prefixFreeBasePath, ignoreCase)) Marker.Match(setOf(packageName)) else null
        }
    }
}