package eu.darken.sdmse.appcontrol.ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class AppControlNavigationModule {

    @Binds
    @IntoSet
    abstract fun appControlNavigation(entry: AppControlNavigation): NavigationEntry
}
