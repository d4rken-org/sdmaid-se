package eu.darken.sdmse.common.upgrade

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class UpgradeModule {
    @Binds
    @Singleton
    abstract fun control(foss: UpgradeRepoFoss): UpgradeRepo

}