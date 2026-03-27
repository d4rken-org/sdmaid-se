package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.IOException
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
inline fun <reified T> kotlinxReader(
    json: Json,
    defaultValue: T,
    fallbackToDefault: Boolean = false,
): (Any?) -> T {
    val serializer = json.serializersModule.serializer(typeOf<T>()) as KSerializer<T>
    return { rawValue ->
        rawValue as String?
        if (rawValue == null) {
            defaultValue
        } else if (fallbackToDefault) {
            try {
                json.decodeFromString(serializer, rawValue) ?: defaultValue
            } catch (e: SerializationException) {
                val tag = logTag("DataStore", "Value", "Kotlinx")
                log(tag, ERROR) { "Failed to parse JSON, using default: ${e.message}" }
                defaultValue
            } catch (e: IllegalArgumentException) {
                val tag = logTag("DataStore", "Value", "Kotlinx")
                log(tag, ERROR) { "Failed to read JSON, using default: ${e.message}" }
                defaultValue
            } catch (e: IOException) {
                val tag = logTag("DataStore", "Value", "Kotlinx")
                log(tag, ERROR) { "Failed to read JSON, using default: ${e.message}" }
                defaultValue
            }
        } else {
            json.decodeFromString(serializer, rawValue) ?: defaultValue
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> kotlinxWriter(
    json: Json,
): (T) -> Any? {
    val serializer = json.serializersModule.serializer(typeOf<T>()) as KSerializer<T>
    return { newValue: T ->
        newValue?.let { json.encodeToString(serializer, it) }
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T,
    json: Json,
    fallbackToDefault: Boolean = false,
) = DataStoreValue(
    dataStore = this,
    key = stringPreferencesKey(key),
    reader = kotlinxReader(json, defaultValue, fallbackToDefault),
    writer = kotlinxWriter(json),
)
