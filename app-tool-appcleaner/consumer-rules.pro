# Stock filters and their factories are logged by class name (KClass.simpleName / default Object.toString());
# keep the names readable in obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
-keepnames class * implements eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter$Factory
