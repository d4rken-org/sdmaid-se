package eu.darken.sdmse.common.adb.service

import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.sharedresource.runSessionAction


@Suppress("UNCHECKED_CAST")
suspend fun <R, T> AdbServiceClient.runModuleAction(
    moduleClass: Class<out R>,
    action: suspend (R) -> T
): T = runSessionAction { session ->
    if (Bugs.isTrace) {
        log(AdbServiceClient.TAG, VERBOSE) { "runModuleAction(moduleClass=$moduleClass, action=$action)" }
    }
    val module = session.clientModules.single { moduleClass.isInstance(it) } as R
    return@runSessionAction action(module)
}