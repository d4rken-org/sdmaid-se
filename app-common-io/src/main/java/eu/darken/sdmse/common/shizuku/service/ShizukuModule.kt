package eu.darken.sdmse.common.shizuku.service

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.migration.DisableInstallInCheck
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

/**
 * Installed in non-hilt [ShizukuComponent]
 */
@DisableInstallInCheck
@Module
class ShizukuModule {

    @Provides
    @Singleton
    fun sharedShell(@AppScope scope: CoroutineScope, dispatcherProvider: DispatcherProvider): SharedShell {
        return SharedShell(ShizukuHost.TAG + "-sharedShell", scope + dispatcherProvider.IO)
    }

    @Provides
    @Singleton
    @ApplicationContext
    fun shizukuContext(context: Context): Context {
        return context
    }

}