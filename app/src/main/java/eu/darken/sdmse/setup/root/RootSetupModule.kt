package eu.darken.sdmse.setup.root

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@Reusable
class RootSetupModule @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootSettings: RootSettings,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)
    override val state = refreshTrigger.mapLatest {
        return@mapLatest State(
            useRoot = rootSettings.useRoot.value(),
        )
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseRoot(useRoot: Boolean) {
        log(TAG) { "toggleUseRoot(useRoot=$useRoot)" }
        rootSettings.useRoot.value(useRoot)
    }

    data class State(
        val useRoot: Boolean?,
    ) : SetupModule.State {

        override val isComplete: Boolean = useRoot != null

        data class PathAccess(
            val label: CaString,
            val localPath: LocalPath,
            val hasAccess: Boolean,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: RootSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Root", "Module")
    }
}