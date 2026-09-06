# Play Core KTX references this compile-time-only GMS annotation not on the runtime classpath
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Prevent R8 from merging exception classes, which masks real exception types in crash reports
-keep,allowshrinking class * extends java.lang.Throwable

# Accessed via reflection.
-keep class eu.darken.sdmse.BuildConfig { *; }

# Setup card items are logged by class name when no card branch handles them (SetupScreen);
# keep the names readable in obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.setup.SetupCardItem

# Dashboard items are logged by class name in the verbose flow diagnostics (DashboardViewModel);
# keep the names readable in obfuscated gplay logs. Names only, members and shrinking unaffected.
-keepnames class * implements eu.darken.sdmse.main.ui.dashboard.cards.DashboardItem
