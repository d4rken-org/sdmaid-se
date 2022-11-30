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
import okio.Sink
import okio.Source
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewaySwitch @Inject constructor(
    private val safGateway: SAFGateway,
    private val localGateway: LocalGateway,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : APathGateway<APath, APathLookup<APath>> {

//    /**
//     * A context appropriate for blocking IO
//     */
//    val gatewayContext: CoroutineContext
//        get() = dispatcherProvider.IO
//
//    suspend fun <T> runIO(action: suspend CoroutineScope.() -> T) = withContext(dispatcherProvider.IO) {
//        action()
//    }

    suspend fun <T : APath> getGateway(path: T): APathGateway<T, APathLookup<T>> {
        @Suppress("UNCHECKED_CAST")
        return getGateway(path.pathType) as APathGateway<T, APathLookup<T>>
    }

    suspend fun getGateway(type: APath.PathType): APathGateway<*, *> {
        val gateway = when (type) {
            APath.PathType.SAF -> safGateway
            APath.PathType.LOCAL -> localGateway
            else -> throw NotImplementedError()
        }
        gateway.addParent(this)
        return gateway
    }

    override val sharedResource = SharedResource.createKeepAlive(
        "${TAG}:SharedResource",
        appScope + dispatcherProvider.IO
    )

    override suspend fun createDir(path: APath): Boolean {
        return getGateway(path).createDir(path)
    }

    override suspend fun createFile(path: APath): Boolean {
        return getGateway(path).createFile(path)
    }

    override suspend fun lookup(path: APath): APathLookup<APath> {
        return getGateway(path).lookup(path)
    }

    override suspend fun lookupFiles(path: APath): List<APathLookup<APath>> {
        return getGateway(path).lookupFiles(path)
    }

    override suspend fun listFiles(path: APath): List<APath> {
        return getGateway(path).listFiles(path)
    }

    override suspend fun exists(path: APath): Boolean {
        return getGateway(path).exists(path)
    }

    override suspend fun canWrite(path: APath): Boolean {
        return getGateway(path).canWrite(path)
    }

    override suspend fun canRead(path: APath): Boolean {
        return getGateway(path).canRead(path)
    }

    override suspend fun read(path: APath): Source {
        return getGateway(path).read(path)
    }

    override suspend fun write(path: APath): Sink {
        return getGateway(path).write(path)
    }

    override suspend fun delete(path: APath): Boolean {
        return getGateway(path).delete(path)
    }

    override suspend fun createSymlink(linkPath: APath, targetPath: APath): Boolean {
        return getGateway(linkPath).createSymlink(linkPath, targetPath)
    }

    override suspend fun setModifiedAt(path: APath, modifiedAt: Date): Boolean {
        return getGateway(path).setModifiedAt(path, modifiedAt)
    }

    override suspend fun setPermissions(path: APath, permissions: Permissions): Boolean {
        return getGateway(path).setPermissions(path, permissions)
    }

    override suspend fun setOwnership(path: APath, ownership: Ownership): Boolean {
        return getGateway(path).setOwnership(path, ownership)
    }

    fun tryReleaseResources(path: APath) {
        if (path is SAFPath) {
            safGateway.releasePermission(path)
        }
    }

    companion object {
        val TAG = logTag("GatewaySwitch")
    }
}