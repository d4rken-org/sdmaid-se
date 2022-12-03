package eu.darken.sdmse.common.clutter.manual

import dagger.Reusable
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import javax.inject.Inject

@Reusable
class ProductionMarkerSource @Inject constructor(
    pkgOps: PkgOps,
    jsonMarkerParser: JsonMarkerParser,
) : ManualMarkerSource(
    pkgOps,
    { jsonMarkerParser.fromAssets("clutter/db_clutter_markers.json") }
)