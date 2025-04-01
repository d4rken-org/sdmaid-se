package eu.darken.sdmse.common.debug

import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.automation.core.debug.DebugTask
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.navigation.navVia
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.sharedresource.runSessionAction
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.DashboardFragmentDirections
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class DebugCardProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val debugSettings: DebugSettings,
    private val rootSettings: RootSettings,
    private val rootManager: RootManager,
    private val adbSettings: AdbSettings,
    private val shizukuManager: ShizukuManager,
    private val rootClient: RootServiceClient,
    private val pkgRepo: PkgRepo,
    private val dataAreaManager: DataAreaManager,
    private val gatewaySwitch: GatewaySwitch,
    private val shellOps: ShellOps,
    private val shizukuClient: AdbServiceClient,
    private val automation: AutomationManager,
    private val taskManager: TaskManager,
) {

    private val rootTestState = MutableStateFlow<RootTestResult?>(null)
    private val shizukuTestState = MutableStateFlow<ShizukuTestResult?>(null)
    private val acsDebugJobState = MutableStateFlow<Job?>(null)

    fun create(vm: ViewModel3) = combine(
        debugSettings.isDebugMode.flow.distinctUntilChanged(),
        debugSettings.isTraceMode.flow.distinctUntilChanged(),
        debugSettings.isDryRunMode.flow.distinctUntilChanged(),
        rootTestState,
        shizukuTestState,
        acsDebugJobState,
    ) { isDebug, isTrace, isDryRun, rootState, shizukuState, acsDebugJob ->
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
                    pkgRepo.refresh()
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
                        hasUserConsent = rootSettings.useRoot.value(),
                        magiskGranted = rootManager.isRooted(),
                        serviceLaunched = withTimeoutOrNull(10 * 1000) {
                            val sb = StringBuilder()

                            val base = try {
                                rootClient.runSessionAction { it.ipc.checkBase() }
                            } catch (e: Exception) {
                                e.message
                            }
                            sb.append("BaseCheck:\n$base")

                            var opsError: String? = null
                            val opsResult = try {
                                shellOps.execute(ShellOpsCmd("whoami"), ShellOps.Mode.ROOT)
                            } catch (e: Exception) {
                                opsError = e.message
                                null
                            }
                            sb.append("ShellOps 'whoami':\n${opsResult ?: opsError}")

                            sb.toString()
                        }
                    )
                }
            },
            shizukuTestResult = shizukuState,
            onTestShizuku = {
                vm.launch {
                    shizukuTestState.value = ShizukuTestResult(
                        testId = UUID.randomUUID().toString(),
                        hasUserConsent = adbSettings.useShizuku.value(),
                        isInstalled = shizukuManager.isInstalled(),
                        isGranted = shizukuManager.isGranted(),
                        serviceLaunched = withTimeoutOrNull(10 * 1000) {
                            val sb = StringBuilder()

                            val base = try {
                                shizukuClient.runSessionAction { it.ipc.checkBase() }
                            } catch (e: Exception) {
                                e
                            }
                            sb.append("BaseCheck:\n$base\n")

                            var opsError: String? = null
                            val opsResult = try {
                                shellOps.execute(ShellOpsCmd("whoami"), ShellOps.Mode.ADB)
                            } catch (e: Exception) {
                                opsError = e.message
                                null
                            }
                            sb.append("ShellOps 'whoami':\n${opsResult ?: opsError}")

                            sb.toString()
                        }
                    )
                }
            },
            onRunTest = {
                vm.launch {
                    gatewaySwitch.useRes {
                        measureTimeMillis {

                        }.also {
                            log(TAG, INFO) { "Deletion took $it" }
                        }
                    }
                }
            },
            onAcsDebug = {
                if (acsDebugJob != null) {
                    acsDebugJob.cancel()
                    acsDebugJobState.value = null
                } else {
                    acsDebugJobState.value = appScope.launch { automation.submit(DebugTask()) }
                }
            },
            acsTask = acsDebugJob,
        )
    }

    data class RootTestResult(
        val testId: String,
        val hasUserConsent: Boolean?,
        val magiskGranted: Boolean,
        val serviceLaunched: String?,
    )

    data class ShizukuTestResult(
        val testId: String,
        val hasUserConsent: Boolean?,
        val isInstalled: Boolean,
        val isGranted: Boolean?,
        val serviceLaunched: String?,
    )

    companion object {
        private val TAG = logTag("Debug", "Card", "Provider")
    }
}