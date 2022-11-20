package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi

inline fun <reified T> moshiReader(
    moshi: Moshi,
    defaultValue: T,
): (Any?) -> T {
    val adapter = moshi.adapter(T::class.java)
    return { rawValue ->
        rawValue as String?
        rawValue?.let { adapter.fromJson(it) } ?: defaultValue
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
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = moshiReader(moshi, defaultValue),
    writer = moshiWriter(moshi),
)