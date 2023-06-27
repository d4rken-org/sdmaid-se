package eu.darken.sdmse.common.root.service

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
 * Installed in non-hilt [RootComponent]
 */
@DisableInstallInCheck
@Module
class RootModule {

    @Provides
    @Singleton
    fun sharedShell(@AppScope scope: CoroutineScope, dispatcherProvider: DispatcherProvider): SharedShell {
        return SharedShell(RootHost.TAG + "-sharedShell", scope + dispatcherProvider.IO)
    }

    @Provides
    @Singleton
    @ApplicationContext
    fun rootContext(context: Context): Context {
        return context
    }

}