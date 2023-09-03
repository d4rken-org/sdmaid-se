package eu.darken.sdmse.main.core.motd

import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.replayingShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotdRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val endpoint: MotdEndpoint,
    private val settings: MotdSettings,
) {
    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    val motd: Flow<Motd?> = refreshTrigger
        .map {
            try {
                endpoint.getMotd(Locale.getDefault())
                    ?.takeIf { it.minimumVersion != null && it.minimumVersion <= BuildConfigWrap.VERSION_CODE }
                    .also { settings.lastMotd.value(it) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to retrieve MOTD" }
                null
            }
        }
        .onStart { emit(settings.lastMotd.value()) }
        .flatMapLatest { motd -> settings.lastDismissedMotd.flow.map { motd to it } }
        .map { (motd, dismissedId) ->
            if (motd == null || motd.id == dismissedId) return@map null
            else motd
        }
        .replayingShare(scope)

    suspend fun dismiss(motd: Motd) {
        log(TAG) { "dismiss(${motd.id})" }
        settings.lastDismissedMotd.value(motd.id)
    }

    companion object {
        private val TAG = logTag("Motd", "Repo")
    }
}