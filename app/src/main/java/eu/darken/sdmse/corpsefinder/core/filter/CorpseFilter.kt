package eu.darken.sdmse.corpsefinder.core.filter

import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class CorpseFilter(
    private val tag: String,
) : Progress.Host, Progress.Client {

    private val progressPub = MutableStateFlow<Progress.Data?>(null)
    override val progress: Flow<Progress.Data?> = progressPub

    override fun updateProgress(update: (Progress.Data?) -> Progress.Data?) {
        progressPub.value = update(progressPub.value)
    }

    abstract suspend fun scan(): Collection<Corpse>

}