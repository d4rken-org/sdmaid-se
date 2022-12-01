package eu.darken.sdmse.common.worker

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class WorkManagerModule {

    @Provides
    @Singleton
    fun workManager(context: Context): WorkManager = WorkManager.getInstance(context)
}
