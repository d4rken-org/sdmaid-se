package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalGateway
import eu.darken.sdmse.common.files.core.saf.SAFGateway
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import okio.Sink
import okio.Source
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewaySwitch @Inject constructor(
    private val safGateway: SAFGateway,
    private val localGateway: LocalGateway,
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : APathGateway<APath, APathLookup<APath>> {

    private suspend fun <T : APath, R> useGateway(
        path: T,
        action: suspend APathGateway<T, APathLookup<T>>.() -> R
    ): R {
        @Suppress("UNCHECKED_CAST")
        val targetGateway = getGateway(path.pathType) as APathGateway<T, APathLookup<T>>
        return action(targetGateway)
    }

    private suspend fun resolveGatewayType(type: APath.PathType): APathGateway<*, *> {
        val gateway = when (type) {
            APath.PathType.SAF -> {
                safGateway.also { adoptChildResource(it) }
            }
            APath.PathType.LOCAL -> {
                localGateway.also { adoptChildResource(it) }
            }
            else -> throw NotImplementedError()
        }
        return gateway
    }

    suspend fun getGateway(type: APath.PathType): APathGateway<*, *> {
        return resolveGatewayType(type)
    }

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    override suspend fun createDir(path: APath): Boolean {
        return useGateway(path) { createDir(path) }
    }

    override suspend fun createFile(path: APath): Boolean {
        return useGateway(path) { createFile(path) }
    }

    override suspend fun lookup(path: APath): APathLookup<APath> {
        return useGateway(path) { lookup(path) }
    }

    override suspend fun lookupFiles(path: APath): Collection<APathLookup<APath>> {
        return useGateway(path) { lookupFiles(path) }
    }

    override suspend fun listFiles(path: APath): Collection<APath> {
        return useGateway(path) { listFiles(path) }
    }

    override suspend fun exists(path: APath): Boolean {
        return useGateway(path) { exists(path) }
    }

    override suspend fun canWrite(path: APath): Boolean {
        return useGateway(path) { canWrite(path) }
    }

    override suspend fun canRead(path: APath): Boolean {
        return useGateway(path) { canRead(path) }
    }

    override suspend fun read(path: APath): Source {
        return useGateway(path) { read(path) }
    }

    override suspend fun write(path: APath): Sink {
        return useGateway(path) { write(path) }
    }

    override suspend fun delete(path: APath) {
        return useGateway(path) { delete(path) }
    }

    override suspend fun createSymlink(linkPath: APath, targetPath: APath): Boolean {
        return useGateway(linkPath) { createSymlink(linkPath, targetPath) }
    }

    override suspend fun setModifiedAt(path: APath, modifiedAt: Instant): Boolean {
        return useGateway(path) { setModifiedAt(path, modifiedAt) }
    }

    override suspend fun setPermissions(path: APath, permissions: Permissions): Boolean {
        return useGateway(path) { setPermissions(path, permissions) }
    }

    override suspend fun setOwnership(path: APath, ownership: Ownership): Boolean {
        return useGateway(path) { setOwnership(path, ownership) }
    }

    companion object {
        val TAG = logTag("Gateway", "Switch")
    }
}