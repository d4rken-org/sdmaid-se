package eu.darken.sdmse.common.clutter.manual

import dagger.Reusable
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

@Reusable
class DebugMarkerSource @Inject constructor(
    pkgOps: PkgOps,
    jsonMarkerParser: JsonMarkerParser,
) : ManualMarkerSource(
    pkgOps,
    { jsonMarkerParser.fromAssets("clutter/db_debug_markers.json") }
)