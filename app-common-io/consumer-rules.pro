-keep class android.content.pm.IPackageDataObserver { *; }

# BinderExtensions.getInterface() resolves these AIDL interfaces by name:
# Class.forName("<interface>$Stub").getField("DESCRIPTOR") and
# Class.forName("<interface>$Stub$Proxy").getDeclaredConstructor(IBinder).
# Generated code, so @Keep is not an option.
-keep class eu.darken.sdmse.common.root.service.RootServiceConnection { public static final java.lang.String DESCRIPTOR; }
-keep class eu.darken.sdmse.common.root.service.RootServiceConnection$Stub
-keep class eu.darken.sdmse.common.root.service.RootServiceConnection$Stub$Proxy { <init>(android.os.IBinder); }
-keep class eu.darken.sdmse.common.adb.AdbServiceConnection { public static final java.lang.String DESCRIPTOR; }
-keep class eu.darken.sdmse.common.adb.AdbServiceConnection$Stub
-keep class eu.darken.sdmse.common.adb.AdbServiceConnection$Stub$Proxy { <init>(android.os.IBinder); }
