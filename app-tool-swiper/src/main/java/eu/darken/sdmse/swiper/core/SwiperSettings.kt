package eu.darken.sdmse.swiper.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwiperSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_swiper")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val swapSwipeDirections = dataStore.createValue("swipe.directions.swapped", false)
    val showFileDetailsOverlay = dataStore.createValue("swipe.details.overlay.enabled", true)
    val hapticFeedbackEnabled = dataStore.createValue("swipe.haptic.enabled", true)
    val hasShownGestureOverlay = dataStore.createValue("swipe.gesture.overlay.shown", false)

    companion object {
        const val FREE_VERSION_LIMIT = 50
        const val FREE_VERSION_SESSION_LIMIT = 2
        internal val TAG = logTag("Swiper", "Settings")
    }
}
