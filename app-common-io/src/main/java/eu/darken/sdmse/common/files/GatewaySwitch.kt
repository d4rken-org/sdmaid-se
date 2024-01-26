package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.local.LocalGateway
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFGateway
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.storage.PathMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus
import okio.IOException
import okio.Sink
import okio.Source
import java.time.Instant
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewaySwitch @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val safGateway: SAFGateway,
    private val localGateway: LocalGateway,
    private val mapper: PathMapper,
) : APathGateway<APath, APathLookup<APath>, APathLookupExtended<APath>> {

    private suspend fun <T : APath, R> useGateway(
        path: T,
        action: suspend APathGateway<T, APathLookup<T>, APathLookupExtended<T>>.() -> R
    ): R {
        @Suppress("UNCHECKED_CAST")
        val targetGateway = getGateway(path.pathType) as APathGateway<T, APathLookup<T>, APathLookupExtended<T>>
        return action(targetGateway)
    }

    private suspend fun resolveGatewayType(type: APath.PathType): APathGateway<*, *, *> {
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

    suspend fun getGateway(type: APath.PathType): APathGateway<*, *, *> {
        return resolveGatewayType(type)
    }

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope + dispatcherProvider.IO)

    override suspend fun createDir(path: APath) {
        return useGateway(path) { createDir(path) }
    }

    override suspend fun createFile(path: APath) {
        return useGateway(path) { createFile(path) }
    }

    override suspend fun lookup(path: APath): APathLookup<APath> {
        return lookup(path, Type.CURRENT)
    }

    suspend fun lookup(path: APath, type: Type): APathLookup<APath> {
        val mapped = path.toTargetType(type)
        return try {
            useGateway(mapped) { lookup(mapped) }
        } catch (e: ReadException) {
            if (type != Type.AUTO) throw e

            val fallback = path.toAlternative()
            useGateway(fallback) { lookup(fallback) }
        }
    }

    override suspend fun lookupFiles(path: APath): Collection<APathLookup<APath>> {
        return lookupFiles(path, Type.CURRENT)
    }

    suspend fun lookupFiles(path: APath, type: Type): Collection<APathLookup<APath>> {
        val mapped = path.toTargetType(type)
        return try {
            useGateway(mapped) { lookupFiles(mapped) }
        } catch (e: ReadException) {
            if (type != Type.AUTO) throw e

            val fallback = path.toAlternative()
            useGateway(fallback) { lookupFiles(fallback) }
        }
    }

    override suspend fun lookupFilesExtended(path: APath): Collection<APathLookupExtended<APath>> {
        return useGateway(path) { lookupFilesExtended(path) }
    }

    override suspend fun walk(
        path: APath,
        filter: (suspend (APathLookup<APath>) -> Boolean)?,
        onError: (suspend (APathLookup<APath>, Exception) -> Boolean)?
    ): Flow<APathLookup<APath>> {
        return useGateway(path) { walk(path, filter, onError) }
    }

    override suspend fun listFiles(path: APath): Collection<APath> {
        return useGateway(path) { listFiles(path) }
    }

    override suspend fun exists(path: APath): Boolean {
        return exists(path, Type.CURRENT)
    }

    suspend fun exists(path: APath, type: Type): Boolean {
        val mapped = path.toTargetType(type)
        return try {
            useGateway(mapped) { exists(mapped) }
        } catch (e: ReadException) {
            if (type != Type.AUTO) throw e

            val fallback = path.toAlternative()
            useGateway(fallback) { exists(fallback) }
        }
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

    private suspend fun APath.toTargetType(type: Type): APath = when (type) {
        Type.AUTO -> this
        Type.CURRENT -> this
        Type.FORCED_LOCAL -> when (this) {
            is LocalPath -> this
            is SAFPath -> mapper.toLocalPath(this) ?: throw IOException("Can't map $this to LOCAL")
            else -> throw IllegalArgumentException("Can't map $this to $type")
        }

        Type.FORCED_SAF -> when (this) {
            is LocalPath -> mapper.toSAFPath(this) ?: throw IOException("Can't map $this to SAF")
            is SAFPath -> this
            else -> throw IllegalArgumentException("Can't map $this to $type")
        }
    }

    private suspend fun APath.toAlternative(): APath = when (this.pathType) {
        APath.PathType.LOCAL -> mapper.toSAFPath(this as LocalPath) ?: throw IOException("Can't map $this to SAF")
        APath.PathType.SAF -> mapper.toLocalPath(this as SAFPath) ?: throw IOException("Can't map $this to LOCAL")
        APath.PathType.RAW -> throw UnsupportedOperationException("Alternative mapping for RAW not available")
    }

    enum class Type {
        CURRENT,
        FORCED_LOCAL,
        FORCED_SAF,
        AUTO
    }

    companion object {
        val TAG = logTag("Gateway", "Switch")
    }
}