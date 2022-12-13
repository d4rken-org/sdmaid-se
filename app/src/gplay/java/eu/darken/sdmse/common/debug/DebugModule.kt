package eu.darken.sdmse.common.debug

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.debug.autoreport.GooglePlayReporting
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class DebugModule {
    @Binds
    @Singleton
    abstract fun autoreporting(foss: GooglePlayReporting): AutomaticBugReporter
}