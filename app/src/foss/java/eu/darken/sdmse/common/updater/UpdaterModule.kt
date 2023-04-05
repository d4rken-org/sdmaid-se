package eu.darken.sdmse.common.updater

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdaterModule {

    @Binds
    @Singleton
    abstract fun updateChecker(checker: FossUpdateChecker): UpdateChecker
}