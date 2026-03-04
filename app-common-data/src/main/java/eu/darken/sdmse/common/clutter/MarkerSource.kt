package eu.darken.sdmse.common.clutter

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.pkgs.Pkg

interface MarkerSource {
    suspend fun getMarkerForLocation(areaType: DataArea.Type): Collection<Marker>
    suspend fun getMarkerForPkg(pkgId: Pkg.Id): Collection<Marker>
    suspend fun match(areaType: DataArea.Type, prefixFreeBasePath: List<String>): Collection<Marker.Match>
}