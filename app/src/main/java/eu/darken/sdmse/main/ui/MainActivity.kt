package eu.darken.sdmse.main.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.LocalNavigationController
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.navigation.NavigationDestination
import eu.darken.sdmse.common.navigation.NavigationEntry
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.UnknownDestinationScreen
import eu.darken.sdmse.common.compose.settings.LocalUpgradeBadgeLabel
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.theming.SdmSeTheme
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.shortcuts.ShortcutManager
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        log(TAG) { "onCreate(restoringState=${savedInstanceState != null})" }

        // Set initial window background to prevent white/black flash before Compose theme loads
        window.decorView.setBackgroundColor(
            if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                0xFF0F1510.toInt() // Dark background matching SdmSeColorsGreen
            } else {
                0xFFF5FBF3.toInt() // Light background matching SdmSeColorsGreen
            }
        )

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        curriculumVitae.updateAppOpened()

        savedIntent = intent

        setContent {
            // Prime WindowInsets to prevent UI jumping on first composition
            val primedInsets = WindowInsets.safeDrawing
            LaunchedEffect(Unit) {
                log(TAG) { "WindowInsets primed: $primedInsets" }
            }

            val themeState by vm.themeState.collectAsState()

            SdmSeTheme(state = themeState) {
                // Update window background to match current theme
                val backgroundColor = MaterialTheme.colorScheme.background
                LaunchedEffect(backgroundColor) {
                    window.decorView.setBackgroundColor(backgroundColor.toArgb())
                }

                CompositionLocalProvider(
                    LocalNavigationController provides navCtrl,
                    LocalUpgradeBadgeLabel provides stringResource(R.string.upgrade_badge_label),
                ) {
                    ErrorEventHandler(vm)
                    NavigationEventHandler(vm)

                    // Keep screen on during tasks
                    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)
                    LaunchedEffect(keepScreenOn) {
                        if (keepScreenOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    // onResume equivalent
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        vm.checkUpgrades()
                        vm.checkErrors()
                    }

                    Navigation()
                }
            }
        }
    }

    @Composable
    private fun Navigation() {
        val backStack = rememberNavBackStack(vm.startRoute)

        LaunchedEffect(Unit) { navCtrl.setup(backStack) }

        // Breadcrumb logging
        LaunchedEffect(backStack.size) {
            Bugs.leaveBreadCrumb("Navigated to ${backStack.lastOrNull()}")
        }

        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { navCtrl.up() },
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                popTransitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                predictivePopTransitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider<NavKey>(
                    fallback = { unknownKey ->
                        // Prevents IllegalStateException when a tool-settings row navigates
                        // to a route whose Fragment screen hasn't been converted yet
                        // (e.g. CustomFilterListRoute, PickerRoute, ArbiterConfigRoute,
                        // ReportsRoute). Tracked as immediate follow-up in the rewrite plan.
                        NavEntry(key = unknownKey) {
                            UnknownDestinationScreen(
                                routeLabel = unknownKey::class.simpleName ?: unknownKey.toString(),
                                onNavigateUp = { navCtrl.up() },
                            )
                        }
                    },
                ) {
                    navigationEntries.forEach { entry ->
                        entry.apply { setup() }
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        log(TAG, VERBOSE) { "onNewIntent() called with action: ${intent.action}" }
        savedIntent = intent
    }

    override fun onResume() {
        super.onResume()
        savedIntent?.let { intent ->
            handleShortcutAction(intent)
            savedIntent = null
        }
    }

    private fun handleShortcutAction(intent: Intent) {
        val shortcutAction = intent.getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION) ?: return
        log(TAG, VERBOSE) { "Handling shortcut action: $shortcutAction" }

        when (shortcutAction) {
            ShortcutActivity.ACTION_OPEN_APPCONTROL -> navCtrl.goTo(AppControlListRoute)
            ShortcutActivity.ACTION_UPGRADE -> navCtrl.goTo(UpgradeRoute())
        }
    }

    private var savedIntent: Intent? = null

    companion object {
        private val TAG = logTag("Main", "Activity")
    }
}
