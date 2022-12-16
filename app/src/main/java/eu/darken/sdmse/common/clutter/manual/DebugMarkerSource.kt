package eu.darken.sdmse.common.clutter.manual

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.clutter.MarkerSource
import eu.darken.sdmse.common.pkgs.PkgRepo
import javax.inject.Inject

@Reusable
class DebugMarkerSource @Inject constructor(
    pkgRepo: PkgRepo,
    jsonMarkerParser: JsonMarkerParser,
) : ManualMarkerSource(
    pkgRepo,
    { jsonMarkerParser.fromAssets("clutter/db_debug_markers.json") }
) {
    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun source(source: DebugMarkerSource): MarkerSource
    }
}