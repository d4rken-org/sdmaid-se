package eu.darken.sdmse.common.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Generic [ConfigBackupContributor] base for any settings class backed by a Preferences [DataStore].
 *
 * It snapshots every set key (minus [excludedKeys]) as a self-describing `{ "t": <tag>, "v": <value> }`
 * pair, which sidesteps the fact that a typed `Preferences.Key` cannot be reconstructed from a name
 * alone. The whole supported key universe is the five types [DataStoreValue][eu.darken.sdmse.common.datastore.DataStoreValue]
 * permits — Boolean/String/Int/Long/Float — so the tag is exhaustive. Complex `@Serializable` settings
 * are stored by the app as JSON strings, so they round-trip losslessly through the String tag.
 *
 * Subclasses are one-liners: point at the [dataStore], give a stable [key], optionally list
 * [excludedKeys] (the local denylist), and bind `@IntoSet`.
 */
abstract class DataStoreSettingsBackupContributor(
    protected val dataStore: DataStore<Preferences>,
) : ConfigBackupContributor {

    /** Preference key names (see [Preferences.Key.name]) to never back up or restore. */
    open val excludedKeys: Set<String> = emptySet()

    override suspend fun snapshot(): JsonElement? {
        val entries = dataStore.data.first().asMap().entries
            .filter { it.key.name !in excludedKeys }
        log(TAG) { "snapshot($key): ${entries.size} keys" }
        if (entries.isEmpty()) return null
        return buildJsonObject {
            entries.forEach { (prefKey, value) -> put(prefKey.name, value.toTagged()) }
        }
    }

    override suspend fun restore(data: JsonElement, mode: RestoreMode) {
        val section = data.jsonObject
        log(TAG) { "restore($key, mode=$mode): ${section.size} keys" }
        dataStore.edit { prefs ->
            if (mode == RestoreMode.REPLACE) {
                // Reset only the keys we manage; excluded keys keep their on-device value.
                prefs.asMap().keys
                    .filter { it.name !in excludedKeys }
                    .forEach { prefs.remove(it) }
            }
            section.forEach { (name, element) ->
                if (name in excludedKeys) return@forEach
                prefs.applyTagged(name, element.jsonObject)
            }
        }
    }

    private fun Any.toTagged(): JsonObject = when (this) {
        is Boolean -> tagged(TAG_BOOL, JsonPrimitive(this))
        is String -> tagged(TAG_STRING, JsonPrimitive(this))
        is Int -> tagged(TAG_INT, JsonPrimitive(this))
        is Long -> tagged(TAG_LONG, JsonPrimitive(this))
        is Float -> tagged(TAG_FLOAT, JsonPrimitive(this))
        else -> throw IllegalArgumentException("Unsupported preference type: ${this::class} ($this)")
    }

    private fun tagged(tag: String, value: JsonPrimitive) = buildJsonObject {
        put(KEY_TYPE, tag)
        put(KEY_VALUE, value)
    }

    private fun MutablePreferences.applyTagged(name: String, entry: JsonObject) {
        val tag = entry[KEY_TYPE]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing type tag for '$name'")
        val raw = entry[KEY_VALUE]?.jsonPrimitive
            ?: throw IllegalArgumentException("Missing value for '$name'")
        when (tag) {
            TAG_BOOL -> set(booleanPreferencesKey(name), raw.boolean)
            TAG_STRING -> set(stringPreferencesKey(name), raw.content)
            TAG_INT -> set(intPreferencesKey(name), raw.int)
            TAG_LONG -> set(longPreferencesKey(name), raw.long)
            TAG_FLOAT -> set(floatPreferencesKey(name), raw.float)
            else -> throw IllegalArgumentException("Unknown type tag '$tag' for '$name'")
        }
    }

    companion object {
        private val TAG = logTag("Backup", "DataStoreContributor")
        private const val KEY_TYPE = "type"
        private const val KEY_VALUE = "value"
        private const val TAG_BOOL = "boolean"
        private const val TAG_STRING = "string"
        private const val TAG_INT = "int"
        private const val TAG_LONG = "long"
        private const val TAG_FLOAT = "float"
    }
}
