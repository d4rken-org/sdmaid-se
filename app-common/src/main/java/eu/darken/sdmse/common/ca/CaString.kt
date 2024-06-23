package eu.darken.sdmse.common.ca

import android.content.Context

/**
 * Context aware string
 */
interface CaString {
    fun get(context: Context): String

    fun isEmpty(context: Context): Boolean = this == EMPTY || get(context).isEmpty()

    companion object {
        val EMPTY: CaString = object : CaString {
            override fun get(context: Context): String = ""
        }
    }
}

internal class CachedCaString(val resolv: (Context) -> String) : CaString {

    private lateinit var cache: String

    override fun get(context: Context): String {
        if (::cache.isInitialized) return cache
        synchronized(this) {
            if (!::cache.isInitialized) cache = resolv(context)
            return cache
        }
    }

    override fun toString(): String = if (::cache.isInitialized) {
        "CachedCaString(\"$cache\")"
    } else {
        "CachedCaString(${Integer.toHexString(hashCode())})"
    }
}

fun caString(provider: Context.(Context) -> String): CaString = object : CaString {
    override fun get(context: Context): String = provider(context, context)
}

fun caString(direct: String): CaString = object : CaString {
    override fun get(context: Context): String = direct
    override fun toString(): String = "CaString(\"$direct\")"
}

fun CaString.cache(): CaString = CachedCaString { this.get(it) }

fun String.toCaString(): CaString = caString(this)

fun Int.toCaString(): CaString = caString { getString(this@toCaString) }.cache()

fun Int.toCaString(vararg args: Any): CaString = caString { it.getString(this@toCaString, *args) }.cache()

fun Pair<Int, Array<out Any?>>.toCaString() = caString {
    val (res, args) = this@toCaString
    it.getString(res, *args)
}.cache()

