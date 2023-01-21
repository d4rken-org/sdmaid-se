package eu.darken.sdmse.common.debug

import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.files.core.GatewaySwitch
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.storage.StorageManager2
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
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
    private val safMapper: SAFMapper,
    private val storageManager2: StorageManager2,
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
                        val local1 = LocalPath.build("/storage/emulated/0/Android/data/eu.thedarken.sdm")
                        log { "Local1: $local1" }
                        val saf1 = safMapper.toSAFPath(local1)!!
                        log { "Saf1: $saf1" }
                        val local2 = safMapper.toLocalPath(saf1)!!
                        log { "Local2: $local2" }
                    } catch (e: Exception) {
                        log(ERROR) { "Failed to run test action: ${e.asLog()}" }
                    }
                }
            }
        )
    }
}