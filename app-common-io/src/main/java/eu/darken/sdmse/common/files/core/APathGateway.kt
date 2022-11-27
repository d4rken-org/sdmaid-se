package eu.darken.sdmse.common.files.core

import eu.darken.sdmse.common.sharedresource.HasSharedResource
import okio.Sink
import okio.Source
import java.util.*

interface APathGateway<P : eu.darken.sdmse.common.files.core.APath, PLU : APathLookup<P>> : HasSharedResource<Any> {

    suspend fun createDir(path: P): Boolean

    suspend fun createFile(path: P): Boolean

    suspend fun lookup(path: P): PLU

    suspend fun listFiles(path: P): List<P>

    suspend fun lookupFiles(path: P): List<PLU>

    suspend fun exists(path: P): Boolean

    suspend fun canWrite(path: P): Boolean

    suspend fun canRead(path: P): Boolean

    suspend fun read(path: P): Source

    suspend fun write(path: P): Sink

    suspend fun delete(path: P): Boolean

    suspend fun createSymlink(linkPath: P, targetPath: P): Boolean

    suspend fun setModifiedAt(path: P, modifiedAt: Date): Boolean

    suspend fun setPermissions(path: P, permissions: Permissions): Boolean

    suspend fun setOwnership(path: P, ownership: Ownership): Boolean
}