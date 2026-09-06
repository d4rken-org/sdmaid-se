# Arbiter criteria are logged by class name (ArbiterConfigViewModel); keep the names readable in
# obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.deduplicator.core.arbiter.ArbiterCriterium
