package eu.darken.sdmse.deduplicator.core.scanner.sleuth

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import javax.inject.Inject
import javax.inject.Provider

class HashSleuth @Inject constructor(

) : Sleuth {

    @Reusable
    class Factory @Inject constructor(
        private val settings: DeduplicatorSettings,
        private val sleuthProvider: Provider<HashSleuth>
    ) : Sleuth.Factory {
        override suspend fun isEnabled(): Boolean = settings.isHashSleuthEnabled.value()
        override suspend fun create(): Sleuth = sleuthProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): Factory
    }

    companion object {
        private val TAG = logTag("Deduplicator", "Sleuth", "Hash")
    }
}