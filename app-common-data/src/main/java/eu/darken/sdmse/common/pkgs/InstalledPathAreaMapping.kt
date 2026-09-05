package eu.darken.sdmse.common.pkgs

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.Installed

fun Installed.getPrivateDataDirs(areas: Collection<DataArea>): Collection<APath> {
    val privateAreas = areas
        .filter { it.type == DataArea.Type.PRIVATE_DATA }
        .filter { it.userHandle == userHandle }

    if (privateAreas.isEmpty()) log(TAG, WARN) { "No PRIVATE_DATA areas provided" }

    return privateAreas.map { it.path.child(packageName) }
}


private val TAG = logTag("Pkg", "InstalledPathAreaMapping")
