# CorpseFilter.toString() uses ::class.simpleName; keep the names readable in obfuscated
# gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
