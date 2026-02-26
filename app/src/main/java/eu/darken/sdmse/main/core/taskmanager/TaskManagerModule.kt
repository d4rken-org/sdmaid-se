package eu.darken.sdmse.main.core.taskmanager

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskManagerModule {

    @Binds
    @Singleton
    abstract fun taskSubmitter(taskManager: TaskManager): TaskSubmitter

    @Binds
    @Singleton
    abstract fun schedulerTaskFactory(impl: SchedulerTaskFactoryImpl): SchedulerTaskFactory
}
