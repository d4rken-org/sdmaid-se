package eu.darken.sdmse.deduplicator.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class DeduplicatorNavigationModule {

    @Binds
    @IntoSet
    abstract fun deduplicatorNavigation(entry: DeduplicatorNavigation): NavigationEntry
}
