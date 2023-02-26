package eu.darken.sdmse.scheduler.core

import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchedulerManager @Inject constructor(

) {

    val state = flowOf(State())


    data class State(
        val todo: Boolean = true
    )
}