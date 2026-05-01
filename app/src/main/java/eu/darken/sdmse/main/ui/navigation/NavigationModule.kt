package eu.darken.sdmse.main.ui.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @IntoSet
    abstract fun appNavigation(entry: AppNavigation): NavigationEntry
}
