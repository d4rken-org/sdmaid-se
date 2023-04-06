package eu.darken.sdmse.common.root.javaroot

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.root.ClientModule
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaRootClient @Inject constructor(
    javaRootHostLauncher: JavaRootHostLauncher,
    @AppScope coroutineScope: CoroutineScope,
    private val rootSettings: RootSettings,
) : SharedResource<JavaRootClient.Connection>(
    TAG,
    coroutineScope,
    flow {
        log(TAG) { "Instantiating RootHost launcher..." }
        if (rootSettings.useRoot.value() != true) throw RootUnavailableException("Root is not enabled")
        emit(javaRootHostLauncher.create())
    }.flattenConcat()
) {

    data class Connection(
        val ipc: JavaRootConnection,
        val clientModules: List<ClientModule>
    ) {
        inline fun <reified T> getModule(): T = clientModules.single { it is T } as T
    }

    suspend fun <T> runSessionAction(action: suspend (Connection) -> T): T = get().use {
        if (Bugs.isTrace) log(TAG, VERBOSE) { "runSessionAction(action=$action)" }
        action(it.item)
    }

    companion object {
        internal val TAG = logTag("Root", "Java", "Client")
    }
}