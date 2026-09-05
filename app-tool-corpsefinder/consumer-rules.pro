# Stock filters and their factories are logged by class name (toString() / default Object.toString());
# keep the names readable in obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
-keepnames class * implements eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter$Factory
