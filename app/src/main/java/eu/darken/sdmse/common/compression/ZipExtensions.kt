package eu.darken.sdmse.common.compression

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

val ZipInputStream.entries: Sequence<Pair<ZipInputStream, ZipEntry>>
    get() = sequence {
        var entry: ZipEntry? = this@entries.nextEntry
        while (entry != null) {
            yield(this@entries to entry)
            entry = this@entries.nextEntry
        }
    }