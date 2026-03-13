package eu.darken.sdmse.common.datastore

import androidx.preference.PreferenceDataStore

open class PreferenceStoreMapper(
    private vararg val dataStoreValues: DataStoreValue<*>
) : PreferenceDataStore() {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> byKey(key: String): DataStoreValue<T> {
        val dataStore = dataStoreValues.singleOrNull { it.keyName == key }
        if (dataStore == null) throw NotImplementedError("No implementation found for key=$key")
        return dataStore as DataStoreValue<T>
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = byKey<Boolean>(key).valueBlocking

    override fun putBoolean(key: String, value: Boolean) {
        byKey<Boolean>(key).valueBlocking = value
    }

    override fun getString(key: String, defValue: String?): String? = byKey<String?>(key).valueBlocking

    override fun putString(key: String, value: String?) {
        byKey<String?>(key).valueBlocking = value
    }

    override fun getInt(key: String, defValue: Int): Int = byKey<Int>(key).valueBlocking

    override fun putInt(key: String, value: Int) {
        byKey<Int>(key).valueBlocking = value
    }

    override fun getLong(key: String, defValue: Long): Long = byKey<Long>(key).valueBlocking

    override fun putLong(key: String, value: Long) {
        byKey<Long>(key).valueBlocking = value
    }

    override fun getFloat(key: String, defValue: Float): Float = byKey<Float>(key).valueBlocking

    override fun putFloat(key: String, value: Float) {
        byKey<Float>(key).valueBlocking = value
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        throw NotImplementedError("putStringSet(key=$key, defValue=$values)")
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> {
        throw NotImplementedError("getStringSet(key=$key, defValue=$defValues)")
    }
}