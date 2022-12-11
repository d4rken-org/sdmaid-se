package eu.darken.sdmse.main.core

import android.os.Parcelable
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import okhttp3.internal.concurrent.Task

interface SDMTool : Progress.Host, HasSharedResource<Any> {

    val type: Type

    suspend fun submit(task: Task): Task.Result

    interface Task : Parcelable {
        val type: Type

        interface Result : Parcelable
    }

    enum class Type {
        CORPSEFINDER
    }
}