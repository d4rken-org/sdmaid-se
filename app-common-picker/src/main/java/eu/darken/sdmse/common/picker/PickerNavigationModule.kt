package eu.darken.sdmse.common.picker

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.navigation.NavigationEntry

@Module
@InstallIn(SingletonComponent::class)
abstract class PickerNavigationModule {

    @Binds
    @IntoSet
    abstract fun pickerNavigation(entry: PickerNavigation): NavigationEntry
}
