package eu.darken.sdmse.squeezer.core.history

import dagger.Reusable
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.GatewaySwitch
import eu.darken.sdmse.common.hashing.Hasher
import eu.darken.sdmse.common.hashing.hash
import eu.darken.sdmse.squeezer.core.ContentId
import eu.darken.sdmse.squeezer.core.ContentIdentifier
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Reusable
class ImageContentHasher @Inject constructor(
    private val gatewaySwitch: GatewaySwitch,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun computeHash(path: APath): ContentIdentifier.ImageHash = withContext(dispatcherProvider.IO) {
        val hash = gatewaySwitch.file(path, readWrite = false).source().hash(Hasher.Type.SHA256).format()
        ContentIdentifier.ImageHash(ContentId(hash))
    }
}
