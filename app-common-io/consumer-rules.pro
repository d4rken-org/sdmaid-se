-keep class android.content.pm.IPackageDataObserver { *; }

-keepclassmembers class eu.darken.sdmse.common.root.service.RootServiceConnection$Stub$Proxy {
  *;
}
-keepclassmembers class eu.darken.sdmse.common.adb.AdbServiceConnection** {
  *;
}