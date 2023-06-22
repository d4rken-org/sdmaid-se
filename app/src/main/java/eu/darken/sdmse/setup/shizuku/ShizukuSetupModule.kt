package eu.darken.sdmse.setup.shizuku

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.shizuku.ShizukuManager
import eu.darken.sdmse.common.shizuku.ShizukuSettings
import eu.darken.sdmse.setup.SetupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val shizukuSettings: ShizukuSettings,
    private val shizukuManager: ShizukuManager,
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    override val state = combine(
        shizukuSettings.useShizuku.flow,
        shizukuManager.shizukuBinder.onStart { emit(null) },
        refreshTrigger
    ) { consent, binder, _ ->

        State(
            isInstalled = shizukuManager.isInstalled(),
            hasConsent = consent,
            isGranted = shizukuManager.isGranted(),
            binderAvailable = binder?.pingBinder() ?: false,
        )
    }

    init {
        shizukuManager.permissionGrantEvents
            .onEach { refresh() }
            .setupCommonEventHandlers(TAG) { "grantEventsMonitor" }
            .launchIn(appScope)
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = rngString
    }

    suspend fun toggleUseShizuku(useShizuku: Boolean?) {
        log(TAG) { "toggleUseShizuku(useShizuku=$useShizuku)" }
        shizukuSettings.useShizuku.value(useShizuku)
    }

    suspend fun requestPermission() {
        log(TAG) { "requestPermission()" }
        shizukuManager.requestPermission()
    }

    data class State(
        val isInstalled: Boolean,
        val hasConsent: Boolean?,
        val isGranted: Boolean,
        val binderAvailable: Boolean
    ) : SetupModule.State {

        override val isComplete: Boolean = !isInstalled || hasConsent == false || (isGranted && binderAvailable)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShizukuSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Shizuku", "Module")
    }
}