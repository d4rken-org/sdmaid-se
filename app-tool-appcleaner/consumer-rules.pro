# AppScanner logs matches by the filter's KClass.simpleName; keep the names readable in
# obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
