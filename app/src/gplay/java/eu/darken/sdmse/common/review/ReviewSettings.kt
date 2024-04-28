package eu.darken.sdmse.common.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.datastore.PreferenceScreenData
import eu.darken.sdmse.common.datastore.PreferenceStoreMapper
import eu.darken.sdmse.common.datastore.createValue
import eu.darken.sdmse.common.debug.logging.logTag
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : PreferenceScreenData {

    private val Context.dataStore by preferencesDataStore(name = "settings_review_gplay")

    override val dataStore: DataStore<Preferences>
        get() = context.dataStore

    val lastDismissed = dataStore.createValue("review.dismissedAt", Instant.EPOCH, moshi)
    val reviewedAt = dataStore.createValue("review.reviewedAt", null as Instant?, moshi)

    override val mapper = PreferenceStoreMapper(

    )

    companion object {
        internal val TAG = logTag("Review", "Settings", "Gplay")
    }
}