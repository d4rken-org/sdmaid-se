package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.flow.DynamicStateFlow
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.corpsefinder.core.Corpse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

abstract class CorpseFilter(
    private val tag: String,
    private val appScope: CoroutineScope,
) : Progress.Host, Progress.Client, HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(tag, appScope)
    private val progressPub = DynamicStateFlow<Progress.Data?>(tag, appScope) { null }
    override val progress: Flow<Progress.Data?> = progressPub.flow

    override fun updateProgress(update: suspend (Progress.Data?) -> Progress.Data?) {
//        progressPub.updateAsync(update)
    }

    abstract suspend fun filter(

    ): Collection<Corpse>

}