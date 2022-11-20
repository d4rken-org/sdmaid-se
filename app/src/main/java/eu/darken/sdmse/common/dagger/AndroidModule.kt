package eu.darken.sdmse.common.dagger

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AndroidModule {

    @Provides
    @Singleton
    fun context(app: Application): Context = app.applicationContext

    @Provides
    @Singleton
    fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun powerManager(context: Context): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides
    @Singleton
    fun batteryManager(context: Context): BatteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    @Provides
    @Singleton
    fun connectivityManager(context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    @Singleton
    fun wifiManager(context: Context): WifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Provides
    @Singleton
    fun workManager(context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun packagemanager(@ApplicationContext context: Context): PackageManager = context.packageManager
}
