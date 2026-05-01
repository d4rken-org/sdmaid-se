package eu.darken.sdmse.common.debug

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.automation.core.AutomationManager
import eu.darken.sdmse.automation.core.debug.DebugTask
import eu.darken.sdmse.common.adb.AdbSettings
import eu.darken.sdmse.common.adb.service.AdbServiceClient
import eu.darken.sdmse.common.adb.shizuku.ShizukuManager
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.files.isDirectory
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.common.forensics.FileForensics
import eu.darken.sdmse.common.navigation.routes.LogViewRoute
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.service.RootServiceClient
import eu.darken.sdmse.common.sharedresource.runSessionAction
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.common.uix.ViewModel2
import eu.darken.sdmse.main.ui.dashboard.DashboardEvents
import eu.darken.sdmse.main.ui.dashboard.cards.DebugDashboardCardItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

class DebugCardProvider @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val debugSettings: DebugSettings,
    private val rootSettings: RootSettings,
    private val rootManager: RootManager,
    private val adbSettings: AdbSettings,
    private val shizukuManager: ShizukuManager,
    private val rootClient: RootServiceClient,
    private val pkgRepo: PkgRepo,
    private val dataAreaManager: DataAreaManager,
    private val shellOps: ShellOps,
    private val shizukuClient: AdbServiceClient,
    private val automation: AutomationManager,
    private val fileForensics: FileForensics,
    private val gatewaySwitch: GatewaySwitch,
) {

    private val rootTestState = MutableStateFlow<RootTestResult?>(null)
    private val shizukuTestState = MutableStateFlow<ShizukuTestResult?>(null)
    private val isCheckingFolders = MutableStateFlow(false)

    fun create(
        vm: ViewModel2,
        onNavigate: (Any) -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onShowEvent: (DashboardEvents) -> Unit = {},
    ) = combine(
        debugSettings.isDebugMode.flow.distinctUntilChanged(),
        debugSettings.isTraceMode.flow.distinctUntilChanged(),
        debugSettings.isDryRunMode.flow.distinctUntilChanged(),
        rootTestState,
        shizukuTestState,
        automation.currentTask,
        isCheckingFolders,
    ) { isDebug, isTrace, isDryRun, rootState, shizukuState, acsTask, checkingFolders ->
        if (!isDebug) return@combine null
        DebugDashboardCardItem(
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
                onNavigate(LogViewRoute)
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

                }
            },
            onAcsDebug = {
                if (acsTask != null) {
                    automation.cancelTask()
                } else {
                    appScope.launch {
                        try {
                            automation.submit(DebugTask())
                        } catch (e: Exception) {
                            if (e !is CancellationException) {
                                onError(e)
                            }
                        }
                    }
                }
            },
            acsTask = acsTask,
            isCheckingUnknownFolders = checkingFolders,
            onCheckUnknownFolders = {
                if (checkingFolders) return@DebugDashboardCardItem
                vm.launch {
                    checkUnknownFolders(vm, onError, onShowEvent)
                }
            },
        )
    }

    private suspend fun checkUnknownFolders(vm: ViewModel2, onError: (Throwable) -> Unit, onShowEvent: (DashboardEvents) -> Unit) {
        isCheckingFolders.value = true
        try {
            val unknownPaths = mutableListOf<String>()
            var scannedCount = 0
            var skippedCount = 0

            gatewaySwitch.sharedResource.get().use {
                val areas = dataAreaManager.currentAreas().filter { it.type == DataArea.Type.SDCARD }
                val multipleAreas = areas.size > 1

                val skipSubpaths = setOf("Android/data", "Android/media", "Android/obb")

                for (area in areas) {
                    val depth1Dirs = try {
                        gatewaySwitch.lookupFiles(area.path).filter { it.isDirectory }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to list ${area.path}: ${e.asLog()}" }
                        skippedCount++
                        continue
                    }

                    val candidates = mutableListOf<Pair<String, eu.darken.sdmse.common.files.APath>>()

                    for (dir in depth1Dirs) {
                        val dirName = dir.lookedUp.path.substringAfterLast('/')

                        // Add depth-1 candidate
                        candidates.add(dirName to dir.lookedUp)

                        // Get depth-2 children
                        val depth2Dirs = try {
                            gatewaySwitch.lookupFiles(dir.lookedUp).filter { it.isDirectory }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, WARN) { "Failed to list ${dir.lookedUp}: ${e.asLog()}" }
                            skippedCount++
                            continue
                        }

                        for (child in depth2Dirs) {
                            val subpath = "$dirName/${child.lookedUp.path.substringAfterLast('/')}"
                            if (subpath in skipSubpaths) continue
                            candidates.add(subpath to child.lookedUp)
                        }
                    }

                    for ((displaySubpath, path) in candidates) {
                        scannedCount++
                        try {
                            val ownerInfo = fileForensics.findOwners(path)
                            if (ownerInfo == null || ownerInfo.owners.isEmpty()) {
                                val displayPath = if (multipleAreas) {
                                    "${area.path.path}/$displaySubpath"
                                } else {
                                    displaySubpath
                                }
                                unknownPaths.add(displayPath)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, WARN) { "Failed findOwners for $path: ${e.asLog()}" }
                            skippedCount++
                        }
                    }
                }
            }

            log(TAG, INFO) { "Unknown folders check: scanned=$scannedCount, skipped=$skippedCount, unknown=${unknownPaths.size}" }
            if (unknownPaths.size > 200) {
                log(TAG, WARN) { "Full unknown list (${unknownPaths.size}):\n${unknownPaths.joinToString("\n")}" }
            }

            withContext(dispatcherProvider.Main) {
                onShowEvent(
                    DashboardEvents.ShowUnknownFolders(
                        unknownPaths = unknownPaths.take(200),
                        scannedCount = scannedCount,
                        skippedCount = skippedCount,
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "checkUnknownFolders failed: ${e.asLog()}" }
            onError(e)
        } finally {
            isCheckingFolders.value = false
        }
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