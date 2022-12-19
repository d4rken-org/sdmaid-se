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
}

fun caString(provider: (Context) -> String): CaString = object : CaString {
    override fun get(context: Context): String = provider(context)
}

fun CaString.cache(): CaString = CachedCaString { this.get(it) }

fun String.toCaString(): CaString = caString { this }

fun ((Context) -> String).toCaString(): CaString = caString { this(it) }

fun Int.toCaString(): CaString = caString { it.getString(this) }.cache()

fun Pair<Int, Array<out Any?>>.toCaString() = caString {
    val (res, args) = this
    it.getString(res, args)
}.cache()

