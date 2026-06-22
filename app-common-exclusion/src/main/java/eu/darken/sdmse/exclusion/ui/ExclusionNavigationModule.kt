package eu.darken.sdmse.exclusion.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class ExclusionNavigationModule {

    @Binds
    @IntoSet
    abstract fun exclusionNavigation(entry: ExclusionNavigation): NavigationEntry
}
