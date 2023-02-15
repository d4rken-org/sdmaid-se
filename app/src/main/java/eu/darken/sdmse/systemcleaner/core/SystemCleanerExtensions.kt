package eu.darken.sdmse.systemcleaner.core

val SystemCleaner.Data?.hasData: Boolean
    get() = this?.filterContents?.isNotEmpty() ?: false