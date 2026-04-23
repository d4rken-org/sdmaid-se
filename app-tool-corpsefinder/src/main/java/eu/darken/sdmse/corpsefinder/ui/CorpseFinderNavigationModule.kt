package eu.darken.sdmse.corpsefinder.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class CorpseFinderNavigationModule {

    @Binds
    @IntoSet
    abstract fun corpseFinderNavigation(entry: CorpseFinderNavigation): NavigationEntry
}
