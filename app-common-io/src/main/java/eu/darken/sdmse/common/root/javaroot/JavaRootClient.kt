package eu.darken.sdmse.common.root.javaroot

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.root.ClientModule
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaRootClient @Inject constructor(
    javaRootHostLauncher: JavaRootHostLauncher,
    @AppScope coroutineScope: CoroutineScope
) : SharedResource<JavaRootClient.Connection>(
    TAG,
    coroutineScope,
    javaRootHostLauncher.create()
) {

    data class Connection(
        val ipc: JavaRootConnection,
        val clientModules: List<ClientModule>
    ) {
        inline fun <reified T> getModule(): T = clientModules.single { it is T } as T
    }

    suspend fun <T> runSessionAction(action: suspend (Connection) -> T): T = get().use {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "runSessionAction(action=$action)" }
        return action(it.item)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <R, T> runModuleAction(
        moduleClass: Class<out R>,
        action: suspend (R) -> T
    ): T = runSessionAction { session ->
        if (Bugs.isTrace) log(TAG, VERBOSE) { "runModuleAction(moduleClass=$moduleClass, action=$action)" }
        val module = session.clientModules.single { moduleClass.isInstance(it) } as R
        return@runSessionAction action(module)
    }

    companion object {
        private val TAG = logTag("Root", "Java", "Client")
    }
}