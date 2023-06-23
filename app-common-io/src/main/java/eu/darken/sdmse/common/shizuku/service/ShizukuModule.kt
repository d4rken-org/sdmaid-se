package eu.darken.sdmse.common.shizuku.service

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.migration.DisableInstallInCheck
import javax.inject.Singleton

/**
 * Installed in non-hilt [ShizukuComponent]
 */
@DisableInstallInCheck
@Module
class ShizukuModule {

    @Provides
    @Singleton
    @ApplicationContext
    fun shizukuContext(context: Context): Context {
        return context
    }

}