package eu.darken.sdmse.common.donate

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.donate.core.DonateRepoGplay
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class DonateModule {
    @Binds
    @Singleton
    abstract fun control(gplay: DonateRepoGplay): DonateRepo

}