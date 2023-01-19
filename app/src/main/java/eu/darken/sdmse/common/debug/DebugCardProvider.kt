package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.files.core.walk
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

class DebugCardProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val debugSettings: DebugSettings,
    private val pkgRepo: PkgRepo,
    private val pkgOps: PkgOps,
    private val dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val fileForensics: FileForensics,
    private val javaRootClient: JavaRootClient,
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
                appScope.launch {
                    try {
                        gatewaySwitch.useRes {
//                            val localGateway = gatewaySwitch.getGateway(APath.PathType.LOCAL) as LocalGateway
//                            log("####") { "Starting read:" }
//                            val result = localGateway.lookupFiles(
//                                LocalPath.build("/data_mirror/data_ce/null/0/com.google.android.setupwizard/files/metrics/SESSION_TYPE_DEFERRED_SETUP"),
//                                mode = LocalGateway.Mode.ROOT
//                            )
                            val result = LocalPath.build("/data/user/0/eu.darken.myperm").walk(gatewaySwitch).toList()
                            log("####") { "Read ${result.size} items" }
                        }
                    } catch (e: Exception) {
                        log(Logging.Priority.ERROR) { "Failed to run test action: ${e.asLog()}" }
                    }
                }
            }
        )
    }
}