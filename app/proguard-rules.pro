-dontobfuscate

# Prevent R8 from merging exception classes, which masks real exception types in crash reports
-keep,allowshrinking class * extends java.lang.Throwable

# Accessed via reflection.
-keep class eu.darken.sdmse.BuildConfig { *; }
