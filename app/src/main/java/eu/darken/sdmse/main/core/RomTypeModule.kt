package eu.darken.sdmse.main.core

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.device.RomTypeProvider
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class RomTypeModule {

    @Provides
    @Singleton
    fun provideRomTypeProvider(generalSettings: GeneralSettings): RomTypeProvider = RomTypeProvider {
        generalSettings.romTypeDetection.value()
    }
}
