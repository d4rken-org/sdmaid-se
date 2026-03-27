package eu.darken.sdmse.exclusion.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.serialization.SerializedStorage
import eu.darken.sdmse.exclusion.core.types.Exclusion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExclusionStorage @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val context: Context,
    json: Json,
) : SerializedStorage<Set<Exclusion>>(dispatcherProvider, json, TAG) {

    override val provideBackupPath: () -> File = { File(context.filesDir, "exclusions") }

    override val provideBackupFileName: () -> String = { "exclusions-v1" }

    override val provideSerializer: () -> KSerializer<Set<Exclusion>> = { SetSerializer(Exclusion.serializer()) }

    companion object {
        private val TAG = logTag("Exclusion", "Storage")
    }
}
