package eu.darken.sdmse.common.clutter

import eu.darken.sdmse.common.areas.DataArea

interface MarkerSource {
    suspend fun getMarkerForLocation(areaType: DataArea.Type): Collection<Marker>
    suspend fun getMarkerForPackageName(packageName: String): Collection<Marker>
    suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: String): Collection<Marker.Match>
}