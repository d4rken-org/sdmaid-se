# Input events are logged by class name (InputInjector.inject); keep the names readable in
# obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * extends eu.darken.sdmse.automation.core.input.InputInjector$Event
