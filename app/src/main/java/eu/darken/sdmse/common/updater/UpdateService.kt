package eu.darken.sdmse.common.updater

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.release.ReleaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateService @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val updateChecker: UpdateChecker,
    generalSettings: GeneralSettings,
    releaseManager: ReleaseManager,
) {

    private val updateCheckTrigger = MutableStateFlow(UUID.randomUUID())
    val availableUpdate: Flow<UpdateChecker.Update?> = flow { emit(updateChecker.isCheckSupported()) }
        .flatMapLatest { isSupported ->
            if (!isSupported) {
                log(TAG) { "Update check is not supported!" }
                return@flatMapLatest flowOf(null)
            }

            combine(
                generalSettings.isOnboardingCompleted.flow,
                generalSettings.isUpdateCheckEnabled.flow,
                updateCheckTrigger
            ) { isOnboardingCompleted, isUpdateCheckEnabled, _ ->
                log(TAG, VERBOSE) { "onboardingComplete=$isOnboardingCompleted, checkEnabled=$isUpdateCheckEnabled" }

                if (!isOnboardingCompleted) {
                    log(TAG, INFO) { "Onboarding is not yet complete!" }
                    return@combine null
                }

                if (!isUpdateCheckEnabled) {
                    log(TAG, INFO) { "Update check is not enabled!" }
                    return@combine null
                }

                updateChecker.getUpdate(betaConsent = releaseManager.hasBetaConsent())
            }
        }
        .setupCommonEventHandlers(TAG) { "availableUpdate" }
        .replayingShare(appScope)

    suspend fun startUpdate(update: UpdateChecker.Update) = updateChecker.startUpdate(update)

    suspend fun viewUpdate(update: UpdateChecker.Update) = updateChecker.viewUpdate(update)

    suspend fun dismissUpdate(update: UpdateChecker.Update) = updateChecker.dismissUpdate(update)

    suspend fun refresh() {
        log(TAG) { "refresh()" }
        updateCheckTrigger.value = UUID.randomUUID()
    }

    companion object {
        private val TAG = logTag("Updater", "Service")
    }
}