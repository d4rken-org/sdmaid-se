package eu.darken.sdmse.automation.core.input

import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InputInjector @Inject constructor(
    private val adbManager: AdbManager,
    private val rootManager: RootManager,
    private val shellOps: ShellOps,
) {

    sealed class Event(
        val command: String,
        val delayAfter: Long = 100L,
    ) {
        data object DpadUp : Event("input keyevent 19")
        data object DpadDown : Event("input keyevent 20")
        data object DpadLeft : Event("input keyevent 21")
        data object DpadRight : Event("input keyevent 22")
        data object DpadCenter : Event("input keyevent 23")
        data object Enter : Event("input keyevent 66")
        data object Tab : Event("input keyevent 61")
    }

    suspend fun canInject(): Boolean {
        val adb = adbManager.canUseAdbNow()
        val root = rootManager.canUseRootNow()
        log(TAG, VERBOSE) { "canInject(): adb=$adb root=$root" }
        return adb || root
    }

    private suspend fun getShellMode(): ShellOps.Mode = when {
        adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
        rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
        else -> throw IllegalStateException("No ShellOps Mode available for input injection")
    }

    suspend fun inject(event: Event) {
        log(TAG) { "inject($event): ${event.command}" }
        val result = shellOps.execute(ShellOpsCmd(listOf(event.command)), getShellMode())
        log(TAG) { "inject($event) result: $result" }
        if (event.delayAfter > 0) delay(event.delayAfter)
    }

    suspend fun inject(vararg events: Event) {
        log(TAG) { "inject(${events.size} events): ${events.map { it::class.simpleName }}" }
        events.forEach { inject(it) }
    }

    companion object {
        val TAG: String = logTag("Automation", "InputInjector")
    }
}
