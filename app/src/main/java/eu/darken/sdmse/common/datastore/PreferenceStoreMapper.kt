package eu.darken.sdmse.common.datastore

import androidx.preference.PreferenceDataStore

open class PreferenceStoreMapper(
    private vararg val dataStoreValues: DataStoreValue<*>
) : PreferenceDataStore() {

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking as Boolean
        } ?: throw NotImplementedError("getBoolean(key=$key, defValue=$defValue)")
    }

    override fun putBoolean(key: String, value: Boolean) {
        dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking = value
        } ?: throw NotImplementedError("putBoolean(key=$key, defValue=$value)")
    }

    override fun getString(key: String, defValue: String?): String? {
        val flowPref = dataStoreValues.singleOrNull { it.keyName == key }
            ?: throw NotImplementedError("getString(key=$key, defValue=$defValue)")
        return flowPref.valueBlocking as String?
    }

    override fun putString(key: String, value: String?) {
        dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking = value
        } ?: throw NotImplementedError("putString(key=$key, defValue=$value)")
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking as Int
        } ?: throw NotImplementedError("getInt(key=$key, defValue=$defValue)")
    }

    override fun putInt(key: String?, value: Int) {
        dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking = value
        } ?: throw NotImplementedError("putInt(key=$key, defValue=$value)")
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking as Long
        } ?: throw NotImplementedError("getLong(key=$key, defValue=$defValue)")
    }

    override fun putLong(key: String?, value: Long) {
        dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking = value
        } ?: throw NotImplementedError("putLong(key=$key, defValue=$value)")
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking as Float
        } ?: throw NotImplementedError("getFloat(key=$key, defValue=$defValue)")
    }

    override fun putFloat(key: String?, value: Float) {
        dataStoreValues.singleOrNull { it.keyName == key }?.let { flowPref ->
            flowPref.valueBlocking = value
        } ?: throw NotImplementedError("putFloat(key=$key, defValue=$value)")
    }

    override fun putStringSet(key: String?, values: MutableSet<String>?) {
        throw NotImplementedError("putStringSet(key=$key, defValue=$values)")
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String> {
        throw NotImplementedError("getStringSet(key=$key, defValue=$defValues)")
    }
}