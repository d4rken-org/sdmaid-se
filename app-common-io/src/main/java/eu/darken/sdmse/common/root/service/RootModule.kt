package eu.darken.sdmse.common.root.service

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.migration.DisableInstallInCheck
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import eu.darken.sdmse.common.shell.UserProcessShell
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
    @RootProcessShell
    fun rootShell(@AppScope scope: CoroutineScope, dispatcherProvider: DispatcherProvider): SharedShell {
        return SharedShell(RootServiceHost.TAG + "-root", scope + dispatcherProvider.IO)
    }

    @Provides
    @Singleton
    @UserProcessShell
    fun userShell(@AppScope scope: CoroutineScope, dispatcherProvider: DispatcherProvider): SharedShell {
        return SharedShell(RootServiceHost.TAG + "-user", scope + dispatcherProvider.IO)
    }

    @Provides
    @Singleton
    @ApplicationContext
    fun rootContext(context: Context): Context {
        return context
    }

}