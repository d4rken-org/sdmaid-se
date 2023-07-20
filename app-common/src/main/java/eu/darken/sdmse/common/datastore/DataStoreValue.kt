package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking


class DataStoreValue<T : Any?> constructor(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<*>,
    val reader: (Any?) -> T,
    val writer: (T) -> Any?
) {

    val keyName: String
        get() = key.name

    val flow: Flow<T> = dataStore.data
        .map { prefs -> reader(prefs[this.key]) }

    data class Updated<T>(
        val old: T,
        val new: T,
    )

    @Suppress("UNCHECKED_CAST")
    suspend fun update(update: (T) -> T?): Updated<T> {
        val values = arrayOfNulls<Any?>(2)

        dataStore.updateData { prefs ->
            val before = reader(prefs[this.key]).also {
                values[0] = it
            }

            val after: T? = update(before).also {
                values[1] = it ?: reader(null)
            }

            prefs.toMutablePreferences().apply {
                set(this@DataStoreValue.key as Preferences.Key<Any?>, after?.let { writer(it) })
            }.toPreferences()
        }

        return Updated(old = (values[0] as T), new = (values[1] as T))
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: Preferences.Key<*>,
    noinline reader: (rawValue: Any?) -> T,
    noinline writer: (value: T) -> Any?
) = DataStoreValue(
    dataStore = this,
    key = key,
    reader = reader,
    writer = writer
)


@Suppress("UNCHECKED_CAST")
inline fun <reified T> basicKey(key: String, defaultValue: T): Preferences.Key<T> = when (defaultValue) {
    is Boolean? -> booleanPreferencesKey(key) as Preferences.Key<T>
    is String? -> stringPreferencesKey(key) as Preferences.Key<T>
    is Int? -> intPreferencesKey(key) as Preferences.Key<T>
    is Long? -> longPreferencesKey(key) as Preferences.Key<T>
    is Float? -> floatPreferencesKey(key) as Preferences.Key<T>
    else -> throw NotImplementedError()
}

inline fun <reified T> basicReader(
    defaultValue: T
): (rawValue: Any?) -> T = { rawValue ->
    (rawValue ?: defaultValue) as T
}

inline fun <reified T> basicWriter(): (T) -> Any? = { value ->
    when (value) {
        is Boolean -> value
        is String -> value
        is Int -> value
        is Long -> value
        is Float -> value
        null -> null
        else -> throw NotImplementedError()
    }
}

inline fun <reified T : Any?> DataStore<Preferences>.createValue(
    key: String,
    defaultValue: T = null as T
) = createValue(
    key = basicKey(key, defaultValue),
    reader = basicReader(defaultValue),
    writer = basicWriter()
)

suspend fun <T : Any?> DataStoreValue<T>.value(): T = flow.first()

suspend fun <T : Any?> DataStoreValue<T>.value(value: T) = update { value }

var <T : Any?> DataStoreValue<T>.valueBlocking: T
    get() = runBlocking { flow.first() }
    set(value) = runBlocking { update { value } }

