package eu.darken.sdmse.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking


class DataStoreValue<T : Any?>(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<*>,
    val reader: (Any?) -> T,
    val writer: (T) -> Any?
) {
    private val dataStoreTag by lazy {
        "DataStore-${dataStore.file?.name?.removeSuffix(".preferences_pb")}"
    }

    val keyName: String
        get() = key.name

    val flow: Flow<T> = dataStore.data
        .map { prefs -> prefs[this.key] }
        .distinctUntilChanged()
        .map { pref -> reader(pref) }
        .onEach { if (Bugs.isTrace) log(dataStoreTag) { "read $keyName -> $it" } }

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
                val toWrite = after?.let { writer(it) }
                if (Bugs.isTrace) log(dataStoreTag) { "WRITE $keyName <- $toWrite" }
                set(this@DataStoreValue.key as Preferences.Key<Any?>, toWrite)
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

