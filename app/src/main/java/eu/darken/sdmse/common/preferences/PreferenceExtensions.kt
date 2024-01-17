package eu.darken.sdmse.common.preferences

import android.graphics.PorterDuff
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
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


fun Preference.tintIcon(@ColorInt color: Int) {
    if (icon == null) return
    icon = DrawableCompat.wrap(icon!!).apply {
        DrawableCompat.setTint(this, color)
        DrawableCompat.setTintMode(this, PorterDuff.Mode.SRC_IN)
    }
}