package eu.darken.sdmse.common.preferences

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.datastore.valueBlocking


inline fun <reified T> ListPreference.setupWithEnum(preference: DataStoreValue<T>) where  T : Enum<T>, T : EnumPreference<T> {
    isPersistent = false

    val startValue = preference.valueBlocking

    entries = enumValues<T>().map { it.label.get(context) }.toTypedArray()
    entryValues = enumValues<T>().map { it.name }.toTypedArray()
    value = (preference.writer(startValue) as String).removeSurrounding("\"")

    setOnPreferenceChangeListener { _, newValueRaw ->
        val newValue = preference.reader("\"${(newValueRaw)}\"")
        preference.valueBlocking = newValue
        true
    }
}

val PreferenceGroup.children: Sequence<Preference>
    get() = sequence {
        for (i in 0 until preferenceCount) {
            yield(getPreference(i))
        }
    }
