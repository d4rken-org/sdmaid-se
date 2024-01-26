package eu.darken.sdmse.common.files

import eu.darken.sdmse.common.sharedresource.HasSharedResource
import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source
import java.time.Instant

interface APathGateway<
        P : APath,
        PLU : APathLookup<P>,
        PLUE : APathLookupExtended<P>,
        > : HasSharedResource<Any> {

    suspend fun createDir(path: P)

    suspend fun createFile(path: P)

    suspend fun listFiles(path: P): Collection<P>

    suspend fun lookup(path: P): PLU

    suspend fun lookupFiles(path: P): Collection<PLU>

    suspend fun lookupFilesExtended(path: P): Collection<PLUE>

    suspend fun walk(
        path: P,
        onFilter: (suspend (PLU) -> Boolean)? = null,
        onError: (suspend (PLU, Exception) -> Boolean)? = null
    ): Flow<PLU>

    suspend fun exists(path: P): Boolean

    suspend fun canWrite(path: P): Boolean

    suspend fun canRead(path: P): Boolean

    suspend fun read(path: P): Source

    suspend fun write(path: P): Sink

    suspend fun delete(path: P)

    suspend fun createSymlink(linkPath: P, targetPath: P): Boolean

    suspend fun setModifiedAt(path: P, modifiedAt: Instant): Boolean

    suspend fun setPermissions(path: P, permissions: Permissions): Boolean

    suspend fun setOwnership(path: P, ownership: Ownership): Boolean
}