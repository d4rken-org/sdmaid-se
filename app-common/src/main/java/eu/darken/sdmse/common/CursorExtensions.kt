package eu.darken.sdmse.common

import android.database.Cursor
import java.io.Closeable

fun <T : Closeable?> T.closeQuietly() {
    try {
        this?.close()
    } catch (e: Exception) {
    }
}

fun <T : Cursor> T.asSequence(): Sequence<T> {
    return generateSequence { if (moveToNext()) this else null }
}