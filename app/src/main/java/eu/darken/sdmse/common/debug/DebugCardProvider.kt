package eu.darken.sdmse.common.debug

import eu.darken.sdmse.automation.core.AutomationController
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.autoreport.DebugSettings
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.walk
import eu.darken.sdmse.common.navigation.navVia
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.javaroot.JavaRootClient
import eu.darken.sdmse.common.storage.SAFMapper
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.ui.dashboard.DashboardFragmentDirections
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import javax.inject.Inject

class DebugCardProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val debugSettings: DebugSettings,
    private val rootSettings: RootSettings,
    private val rootManager: RootManager,
    private val rootClient: JavaRootClient,
    private val pkgRepo: PkgRepo,
    private val dataAreaManager: DataAreaManager,
    private val pkgOps: PkgOps,
    private val automationController: AutomationController,
    private val gatewaySwitch: GatewaySwitch,
    private val safMapper: SAFMapper,
) {

    private val rootTestState = MutableStateFlow<RootTestResult?>(null)

    fun create(vm: ViewModel3) = combine(
        debugSettings.isDebugMode.flow.distinctUntilChanged(),
        debugSettings.isTraceMode.flow.distinctUntilChanged(),
        debugSettings.isDryRunMode.flow.distinctUntilChanged(),
        rootTestState
    ) { isDebug, isTrace, isDryRun, rootState ->
        if (!isDebug) return@combine null
        DebugCardVH.Item(
            isDryRunEnabled = isDryRun,
            onDryRunEnabled = { debugSettings.isDryRunMode.valueBlocking = it },
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
            onViewLog = {
                DashboardFragmentDirections.actionDashboardFragmentToLogViewFragment().navVia(vm)
            },
            rootTestResult = rootState,
            onTestRoot = {
                vm.launch {
                    rootTestState.value = RootTestResult(
                        testId = UUID.randomUUID().toString(),
                        allowed = rootSettings.useRoot.value(),
                        magiskGranted = rootManager.isRooted(),
                        serviceLaunched = withTimeoutOrNull(10 * 1000) {
                            rootClient.runSessionAction { it.ipc.checkBase() }
                        }
                    )
                }
            },
            onRunTest = {
                vm.launch {
                    val local = LocalPath.build("/storage/emulated/0/Android/data")
                    val saf = safMapper.toSAFPath(local)!!
                    val start = System.currentTimeMillis()

                    val paths = gatewaySwitch.useRes {
                        saf.walk(gatewaySwitch).toList()
                    }

                    val duration = System.currentTimeMillis() - start
                    val durationS = duration / 1000
                    log(TAG) { "${paths.size} items in $duration ms (${paths.size / durationS} item/s)" }
                }
            }
        )
    }

    data class RootTestResult(
        val testId: String,
        val allowed: Boolean?,
        val magiskGranted: Boolean,
        val serviceLaunched: String?,
    )

    companion object {
        private val TAG = logTag("Debug", "Card", "Provider")
    }
}