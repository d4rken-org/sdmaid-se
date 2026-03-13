package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag

inline fun <reified T> moshiReader(
    moshi: Moshi,
    defaultValue: T,
    fallbackToDefault: Boolean = false,
): (Any?) -> T {
    val adapter = moshi.adapter(T::class.java)
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (fallbackToDefault) {
            try {
                adapter.fromJson(rawValue) ?: defaultValue
            } catch (e: Exception) {
                val tag = logTag("DataStore", "Value", "Moshi")
                log(tag, ERROR) { "Failed to parse JSON, using default: ${e.message}" }
                defaultValue
            }
        } else {
            adapter.fromJson(rawValue) ?: defaultValue
        }
    }
}

inline fun <reified T> moshiWriter(
    moshi: Moshi,
): (T) -> Any? {
    val adapter = moshi.adapter(T::class.java)
    return { newValue: T ->
        newValue?.let { adapter.toJson(it) }
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T,
    moshi: Moshi,
    fallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = moshiReader(moshi, defaultValue, fallbackToDefault),
    writer = moshiWriter(moshi),
)