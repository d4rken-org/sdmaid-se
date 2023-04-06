package eu.darken.sdmse.common.root.service

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.migration.DisableInstallInCheck
import eu.darken.sdmse.common.shell.RootProcessShell
import eu.darken.sdmse.common.shell.SharedShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
    fun sharedShell(): SharedShell {
        return SharedShell(RootServiceHost.TAG, GlobalScope + Dispatchers.IO)
    }

    @Provides
    @Singleton
    @ApplicationContext
    fun rootContext(context: Context): Context {
        return context
    }

}