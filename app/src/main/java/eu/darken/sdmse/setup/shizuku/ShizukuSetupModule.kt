package eu.darken.sdmse.setup.shizuku

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.common.areas.DataAreaManager
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuSetupModule @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val shizukuSettings: ShizukuSettings,
    private val shizukuManager: ShizukuManager,
    private val dataAreaManager: DataAreaManager
) : SetupModule {

    private val refreshTrigger = MutableStateFlow(rngString)

    override val state = combine(
        shizukuSettings.useShizuku.flow,
        shizukuManager.shizukuBinder.onStart { emit(null) },
        refreshTrigger
    ) { useShizuku, binder, _ ->

        State(
            useShizuku = useShizuku,
            isCompatible = shizukuManager.isCompatible(),
            isInstalled = shizukuManager.isInstalled(),
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

        if (useShizuku == true && shizukuManager.isGranted() == false) {
            val grantResult = coroutineScope {
                val eventResult = async {
                    shizukuManager.permissionGrantEvents
                        .mapLatest { shizukuManager.isGranted() }
                        .first()
                }

                log(TAG) { "Requesting permission" }
                shizukuManager.requestPermission()

                withTimeoutOrNull(30 * 1000) { eventResult.await() }
            }

            log(TAG) { "Permission grant result was $grantResult" }
            shizukuSettings.useShizuku.value(grantResult.takeIf { it == true })
        } else {
            shizukuSettings.useShizuku.value(useShizuku)
        }

        dataAreaManager.reload()
    }

    data class State(
        val useShizuku: Boolean?,
        val isCompatible: Boolean,
        val isInstalled: Boolean,
        val binderAvailable: Boolean,
    ) : SetupModule.State {

        override val isComplete: Boolean =
            useShizuku == false || !isCompatible || !isInstalled || (binderAvailable && useShizuku == true)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: ShizukuSetupModule): SetupModule
    }

    companion object {
        private val TAG = logTag("Setup", "Shizuku", "Module")
    }
}