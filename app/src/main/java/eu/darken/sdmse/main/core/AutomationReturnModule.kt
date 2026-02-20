package eu.darken.sdmse.main.core

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.automation.core.AutomationReturnHelper
import eu.darken.sdmse.main.ui.MainActivity
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AutomationReturnModule {
    @Provides
    @Singleton
    fun provideAutomationReturnHelper(): AutomationReturnHelper = AutomationReturnHelper { context ->
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
    }
}
