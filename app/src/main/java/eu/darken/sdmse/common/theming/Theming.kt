package eu.darken.sdmse.common.theming

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources.NotFoundException
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.valueBlocking
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Theming @Inject constructor(
    private val application: Application,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) {

    private val activities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    fun setup() {
        log(TAG) { "setup()" }

        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                log(TAG, VERBOSE) { "Adding new activity: $activity" }

                generalSettings.themeMode.valueBlocking.applyMode()
                generalSettings.themeStyle.valueBlocking.applyStyle(setOf(activity))

                // Track so we can recreate it the settings change again
                activities.add(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                log(TAG, VERBOSE) { "Removing activity: $activity" }
                activities.remove(activity)
            }
        }
        application.registerActivityLifecycleCallbacks(callback)

        // Monitor setting changes and affect already created activities
        var oldThemeMode: ThemeMode? = null
        var oldThemeStyle: ThemeStyle? = null
        combine(
            generalSettings.themeMode.flow,
            generalSettings.themeStyle.flow,
        ) { newThemeMode, newThemeStyle ->
            log(TAG) { "oldThemeMode=$oldThemeMode, newThemeMode=$newThemeMode" }
            log(TAG) { "oldThemeStyle=$oldThemeStyle, newhemeStyle=$newThemeStyle" }

            withContext(dispatcherProvider.Main) {
                newThemeMode.applyMode()
            }

            if (oldThemeStyle != null && oldThemeStyle != newThemeStyle) {
                withContext(dispatcherProvider.Main) {
                    // .toSet() to prevent concurrent modifcation issue, can this actually happen on the main thread?
                    activities.toSet().forEach {
                        log(TAG) { "Recreating $it" }
                        it.recreate()
                    }
                }
            }

            oldThemeMode = newThemeMode
            oldThemeStyle = newThemeStyle
        }
            .setupCommonEventHandlers(TAG) { "setup" }
            .launchIn(appScope)

        // Before any Activity is created, to reprevent unnecessary Activity recreation
        generalSettings.themeMode.valueBlocking.let {
            log(TAG) { "Applying initial themeMode setting: $it" }
            it.applyMode()
        }
        generalSettings.themeStyle.valueBlocking.let {
            log(TAG) { "Applying initial themeStyle setting: $it" }
            it.applyStyle()
        }
    }

    private fun ThemeMode.applyMode() = when (this) {
        ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        ThemeMode.DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun ThemeStyle.applyStyle(activities: Set<Activity> = emptySet()) = when (this) {
        ThemeStyle.DEFAULT -> {
            activities.forEach { activity ->
                log(TAG) { "Applying DEFAULT to $activity" }
                // NOOP This should only be called on fresh activities, and for DEFAULT we just do nothing
            }
        }

        ThemeStyle.MATERIAL_YOU -> {
            // We don't use DynamicColors.applyToActivitiesIfAvailable() because we can't remove it again
            activities.forEach { activity ->
                log(TAG) { "Applying MATERIAL_YOU to $activity" }

                DynamicColors.applyToActivityIfAvailable(activity)

                setStatusBarColor(activity, StatusBarStyle.SURFACE)
                setNavBarStyle(activity, NavBarStyle.SURFACE)
            }
        }
    }

    enum class NavBarStyle {
        SURFACE, PRIMARY
    }

    fun setNavBarStyle(activity: Activity, mode: NavBarStyle) {
        log(TAG) { "setNavBarStyle($activity,$mode)" }
        val window = activity.window ?: return
        val uiMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        when (mode) {
            NavBarStyle.PRIMARY -> {
                if (hasApiLevel(30)) {
                    @Suppress("NewApi")
                    window.insetsController?.setSystemBarsAppearance(
                        0,
                        APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }

            NavBarStyle.SURFACE -> {
                if (hasApiLevel(30)) {
                    @Suppress("NewApi")
                    window.insetsController?.setSystemBarsAppearance(
                        APPEARANCE_LIGHT_NAVIGATION_BARS,
                        APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                } else {
                    if (uiMode != Configuration.UI_MODE_NIGHT_YES) {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
            }
        }

        window.navigationBarColor = try {
            activity.getColorForAttr(
                when (mode) {
                    NavBarStyle.SURFACE -> com.google.android.material.R.attr.colorSurface
                    NavBarStyle.PRIMARY -> com.google.android.material.R.attr.colorPrimarySurface
                }
            ).also {
                log(TAG) { "Setting navigationBarColor for $activity to $it" }
            }
        } catch (e: NotFoundException) {
            log(TAG, ERROR) { "Failed to get colorPrimary from theme: ${e.asLog()}" }
            ContextCompat.getColor(
                activity, when (mode) {
                    NavBarStyle.SURFACE -> R.color.md_theme_surface
                    NavBarStyle.PRIMARY -> R.color.md_theme_primary
                }
            )
        }
    }

    enum class StatusBarStyle {
        SURFACE, PRIMARY
    }

    fun setStatusBarColor(activity: Activity, mode: StatusBarStyle) {
        log(TAG) { "setStatusBarColor($activity, $mode)" }
        val window = activity.window ?: return
        window.statusBarColor = try {
            activity.getColorForAttr(
                when (mode) {
                    StatusBarStyle.SURFACE -> com.google.android.material.R.attr.colorSurface
                    StatusBarStyle.PRIMARY -> com.google.android.material.R.attr.colorPrimarySurface
                }
            )
        } catch (e: NotFoundException) {
            log(TAG, ERROR) { "Failed to get colorSurface from theme: ${e.asLog()}" }
            ContextCompat.getColor(
                activity, when (mode) {
                    StatusBarStyle.SURFACE -> R.color.md_theme_surface
                    StatusBarStyle.PRIMARY -> R.color.md_theme_primary
                }
            )
        }
    }

    fun notifySplashScreenDone(activity: Activity) {
        log(TAG, INFO) { "notifySplashScreenDone($activity)" }
        generalSettings.themeStyle.valueBlocking.applyStyle(setOf(activity))
    }

    companion object {
        private val TAG = logTag("Theming")
    }
}