package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.files.core.saf.SAFPath
import eu.darken.sdmse.common.sharedresource.SharedResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.Sink
import okio.Source
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class GatewaySwitch @Inject constructor(
    private val safGateway: SAFGateway,
    private val localGateway: LocalGateway,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : APathGateway<eu.darken.sdmse.common.files.core.APath, APathLookup<eu.darken.sdmse.common.files.core.APath>> {

    /**
     * A context appropriate for blocking IO
     */
    val gatewayContext: CoroutineContext
        get() = dispatcherProvider.IO

    suspend fun <T> runIO(action: suspend CoroutineScope.() -> T) = withContext(dispatcherProvider.IO) {
        action()
    }

    suspend fun <T : eu.darken.sdmse.common.files.core.APath> getGateway(path: T): APathGateway<T, APathLookup<T>> {
        @Suppress("UNCHECKED_CAST")
        val gateway = when (path.pathType) {
            eu.darken.sdmse.common.files.core.APath.PathType.SAF -> safGateway
            eu.darken.sdmse.common.files.core.APath.PathType.LOCAL -> localGateway
            else -> throw NotImplementedError()
        } as APathGateway<T, APathLookup<T>>
        gateway.addParent(this)
        return gateway
    }

    override val sharedResource = SharedResource.createKeepAlive(
        "${TAG}:SharedResource",
        appScope + dispatcherProvider.IO
    )

    override suspend fun createDir(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).createDir(path)
    }

    override suspend fun createFile(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).createFile(path)
    }

    override suspend fun lookup(path: eu.darken.sdmse.common.files.core.APath): APathLookup<eu.darken.sdmse.common.files.core.APath> {
        return getGateway(path).lookup(path)
    }

    override suspend fun lookupFiles(path: eu.darken.sdmse.common.files.core.APath): List<APathLookup<eu.darken.sdmse.common.files.core.APath>> {
        return getGateway(path).lookupFiles(path)
    }

    override suspend fun listFiles(path: eu.darken.sdmse.common.files.core.APath): List<eu.darken.sdmse.common.files.core.APath> {
        return getGateway(path).listFiles(path)
    }

    override suspend fun exists(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).exists(path)
    }

    override suspend fun canWrite(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).canWrite(path)
    }

    override suspend fun canRead(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).canRead(path)
    }

    override suspend fun read(path: eu.darken.sdmse.common.files.core.APath): Source {
        return getGateway(path).read(path)
    }

    override suspend fun write(path: eu.darken.sdmse.common.files.core.APath): Sink {
        return getGateway(path).write(path)
    }

    override suspend fun delete(path: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(path).delete(path)
    }

    override suspend fun createSymlink(linkPath: eu.darken.sdmse.common.files.core.APath, targetPath: eu.darken.sdmse.common.files.core.APath): Boolean {
        return getGateway(linkPath).createSymlink(linkPath, targetPath)
    }

    override suspend fun setModifiedAt(path: eu.darken.sdmse.common.files.core.APath, modifiedAt: Date): Boolean {
        return getGateway(path).setModifiedAt(path, modifiedAt)
    }

    override suspend fun setPermissions(path: eu.darken.sdmse.common.files.core.APath, permissions: Permissions): Boolean {
        return getGateway(path).setPermissions(path, permissions)
    }

    override suspend fun setOwnership(path: eu.darken.sdmse.common.files.core.APath, ownership: Ownership): Boolean {
        return getGateway(path).setOwnership(path, ownership)
    }

    fun tryReleaseResources(path: eu.darken.sdmse.common.files.core.APath) {
        if (path is SAFPath) {
            safGateway.releasePermission(path)
        }
    }

    companion object {
        val TAG = logTag("GatewaySwitch")
    }
}