package eu.darken.sdmse.common.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_review_gplay")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastDismissed = dataStore.createValue("review.dismissedAt", null as Instant?, json)
    val reviewedAt = dataStore.createValue("review.reviewedAt", null as Instant?, json)

    companion object {
        internal val TAG = logTag("Review", "Settings", "Gplay")
    }
}