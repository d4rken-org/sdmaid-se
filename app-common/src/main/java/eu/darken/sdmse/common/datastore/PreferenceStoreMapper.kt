package eu.darken.sdmse.common.datastore

import androidx.preference.PreferenceDataStore

open class PreferenceStoreMapper(
    private vararg val dataStoreValues: DataStoreValue<*>
) : PreferenceDataStore() {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> byKey(key: String) =
        dataStoreValues.singleOrNull { it.keyName == key } as DataStoreValue<T>?

    override fun getBoolean(key: String, defValue: Boolean): Boolean = byKey<Boolean>(key)?.valueBlocking
        ?: throw NotImplementedError("getBoolean(key=$key, defValue=$defValue)")

    override fun putBoolean(key: String, value: Boolean): Unit = byKey<Boolean>(key)
        ?.let { it.valueBlocking = value }
        ?: throw NotImplementedError("putBoolean(key=$key, defValue=$value)")

    override fun getString(key: String, defValue: String?): String? = byKey<String?>(key)?.valueBlocking
        ?: throw NotImplementedError("getString(key=$key, defValue=$defValue)")

    override fun putString(key: String, value: String?): Unit = byKey<String?>(key)
        ?.let { it.valueBlocking = value }
        ?: throw NotImplementedError("putString(key=$key, defValue=$value)")

    override fun getInt(key: String, defValue: Int): Int = byKey<Int>(key)?.valueBlocking
        ?: throw NotImplementedError("getInt(key=$key, defValue=$defValue)")

    override fun putInt(key: String, value: Int): Unit = byKey<Int>(key)
        ?.let { it.valueBlocking = value }
        ?: throw NotImplementedError("putInt(key=$key, defValue=$value)")

    override fun getLong(key: String, defValue: Long): Long = byKey<Long>(key)?.valueBlocking
        ?: throw NotImplementedError("getLong(key=$key, defValue=$defValue)")

    override fun putLong(key: String, value: Long): Unit = byKey<Long>(key)
        ?.let { it.valueBlocking = value }
        ?: throw NotImplementedError("putLong(key=$key, defValue=$value)")

    override fun getFloat(key: String, defValue: Float): Float = byKey<Float>(key)?.valueBlocking
        ?: throw NotImplementedError("getFloat(key=$key, defValue=$defValue)")

    override fun putFloat(key: String, value: Float): Unit = byKey<Float>(key)
        ?.let { it.valueBlocking = value }
        ?: throw NotImplementedError("putFloat(key=$key, defValue=$value)")

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        throw NotImplementedError("putStringSet(key=$key, defValue=$values)")
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> {
        throw NotImplementedError("getStringSet(key=$key, defValue=$defValues)")
    }
}