package eu.darken.sdmse.exclusion.core

import android.content.Context
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.serialization.SerializedStorage
import eu.darken.sdmse.exclusion.core.types.Exclusion
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionStorage @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
) : SerializedStorage<Set<Exclusion>>(dispatcherProvider, TAG) {

    override val provideBackupPath: () -> File = { File(context.filesDir, "exclusions") }

    override val provideBackupFileName: () -> String = { "exclusions-v1" }

    override val provideAdapter: () -> JsonAdapter<Set<Exclusion>> = { moshi.adapter() }

    companion object {
        private val TAG = logTag("Exclusion", "Storage")
    }
}