package eu.darken.sdmse.main.core

import android.os.Parcelable
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import kotlinx.coroutines.flow.Flow

interface SDMTool : Progress.Host, Progress.Client, HasSharedResource<Any> {

    val type: Type

    val state: Flow<State>

    interface State

    suspend fun submit(task: Task): Task.Result

    interface Task : Parcelable {
        val type: Type

        interface Result : Parcelable {
            val type: Type

            val primaryInfo: CaString
            val secondaryInfo: CaString? get() = null
        }
    }

    enum class Type {
        CORPSEFINDER,
        SYSTEMCLEANER,
        APPCLEANER,
        APPCONTROL,
        ANALYZER,
    }
}