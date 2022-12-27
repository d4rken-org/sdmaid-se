package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.lookupFiles
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class DebugCardProvider @Inject constructor(
    private val debugSettings: DebugSettings,
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
) {

    fun create(vm: ViewModel3) = combine(
        debugSettings.isDebugMode.flow,
        debugSettings.isTraceMode.flow
    ) { isDebug, isTrace ->
        if (!isDebug) return@combine null
        DebugCardVH.Item(
            isTraceEnabled = isTrace,
            onTraceEnabled = { debugSettings.isTraceMode.valueBlocking = it },
            onReloadAreas = {
                vm.launch { dataAreaManager.reload() }
            },
            onReloadPkgs = {
                vm.launch {
                    pkgRepo.reload()
                }
            },
            onRunTest = {
                vm.launch {
                    LocalPath.build("/sdcard/Download").lookupFiles(gatewaySwitch)
                    fileForensics.findOwners(LocalPath.build("/data/dalvik-cache/arm64/apex@com.android.permission@priv-app@GooglePermissionController@M_2022_06@GooglePermissionController.apk@classes.dex"))
//                    pkgOps.queryPkg("com.google.android.permissioncontroller".toPkgId()).let {
//                        log { " Queried PKGS: $it" }
//                    }
//                    pkgOps.viewArchive(
//                        LocalPath.build("/data/app/com.google.android.trichromelibrary_524907933--Y3-0N2FPgdz_idXlQjVYQ==/base.apk")
//                    ).let {
//                        log { "trichrome pkg: $it" }
//                    }
//                    val path = LocalPath.build("/storage/emulated/0/")
//                    path.walk(
//                        gatewaySwitch,
//                        filter = { item -> item.segments.size <= 4 + path.segments.size }
//                    )
//                        .collectLatest {
//                            log { "# PATH: $it" }
//                        }
                }
            }
        )
    }
}