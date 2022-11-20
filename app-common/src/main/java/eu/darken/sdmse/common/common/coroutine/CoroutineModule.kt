package eu.darken.sdmse.common.coroutine

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@InstallIn(SingletonComponent::class)
@Module
abstract class CoroutineModule {

    @Binds
    abstract fun dispatcherProvider(defaultProvider: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @AppScope
    abstract fun appscope(appCoroutineScope: AppCoroutineScope): CoroutineScope
}
