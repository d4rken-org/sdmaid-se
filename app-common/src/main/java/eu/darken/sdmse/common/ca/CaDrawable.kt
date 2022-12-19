package eu.darken.sdmse.common.ca

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Context aware drawable
 */
interface CaDrawable {
    fun get(context: Context): Drawable
}

internal class CachedCaDrawable(val resolv: (Context) -> Drawable) : CaDrawable {

    private lateinit var cache: Drawable

    override fun get(context: Context): Drawable {
        if (::cache.isInitialized) return cache
        synchronized(this) {
            if (!::cache.isInitialized) cache = resolv(context)
            return cache
        }
    }
}

fun caDrawable(provider: (Context) -> Drawable): CaDrawable = object : CaDrawable {
    override fun get(context: Context): Drawable = provider(context)
}

fun CaDrawable.cache(): CaDrawable = CachedCaDrawable { this.get(it) }

fun Drawable.toCaDrawable(): CaDrawable = caDrawable { this }

fun ((Context) -> Drawable).toCaDrawable(): CaDrawable = caDrawable { this(it) }

fun Int.toCaDrawable(): CaDrawable = caDrawable { ContextCompat.getDrawable(it, this)!! }.cache()
