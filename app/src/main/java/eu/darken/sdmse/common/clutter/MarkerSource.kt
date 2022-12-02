package eu.darken.sdmse.common.clutter

import eu.darken.sdmse.common.storageareas.StorageArea

interface MarkerSource {
    suspend fun getMarkerForLocation(areaType: StorageArea.Type): Collection<Marker>
    suspend fun getMarkerForPackageName(packageName: String): Collection<Marker>
    suspend fun match(areaType: StorageArea.Type, prefixFreeBasePath: String): Collection<Marker.Match>
}