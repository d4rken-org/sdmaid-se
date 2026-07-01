package eu.darken.sdmse.main.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import eu.darken.sdmse.common.navigation.ModalBottomSheetSceneStrategy
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.device.DeviceDetective
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logviewer.ui.FloatingLogPanelHost
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
import eu.darken.sdmse.common.compose.tour.GuidedTourHost
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.DeviceStorageRoute
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.theming.SdmSeTheme
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.shortcuts.ShortcutManager
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var guidedTourController: GuidedTourController
    @Inject lateinit var deviceDetective: DeviceDetective

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

        // Fresh launch: fold a shortcut/widget intent straight into the initial back stack (see
        // Navigation()), so we never navigate as a second step. A second-step goTo() would race the
        // back-stack setup on a CLEAR_TASK recreate (the widget/launcher intents all CLEAR_TASK, and
        // MainActivity is launchMode=standard) and get discarded, landing on the Dashboard. Gating on
        // savedInstanceState avoids re-consuming the same intent after a config change. onNewIntent
        // deliveries to an already-live instance still route via onResume → handleShortcutAction.
        launchRoute = if (savedInstanceState == null) shortcutRoute(intent) else null

        setContent {
            // Prime WindowInsets to prevent UI jumping on first composition
            val primedInsets = WindowInsets.safeDrawing
            LaunchedEffect(Unit) {
                log(TAG) { "WindowInsets primed: $primedInsets" }
            }

            val themeState by vm.themeState.collectAsStateWithLifecycle()

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

                    // The floating debug log panel is a sibling overlay above the nav graph (but
                    // below dialogs/popups, which render in their own windows). Empty areas of the
                    // overlay don't intercept touches, so the app underneath stays interactive.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Navigation()
                        FloatingLogPanelHost()
                    }
                }
            }
        }
    }

    @Composable
    private fun Navigation() {
        // On a fresh launch this seeds [startRoute, launchRoute]; launchRoute is null otherwise. On a
        // config change rememberNavBackStack restores the saved stack and ignores these args.
        val backStack = rememberNavBackStack(*listOfNotNull<NavKey>(vm.startRoute, launchRoute).toTypedArray())

        LaunchedEffect(Unit) { navCtrl.setup(backStack) }

        // Breadcrumb logging
        LaunchedEffect(backStack.size) {
            Bugs.leaveBreadCrumb("Navigated to ${backStack.lastOrNull()}")
        }

        // Notify guided-tour controller of route changes (used for non-click-protected
        // tours that auto-complete when the user navigates elsewhere).
        LaunchedEffect(backStack.lastOrNull()) {
            guidedTourController.onRouteChanged(backStack.lastOrNull())
        }

        val coroutineScope = rememberCoroutineScope()

        CompositionLocalProvider(LocalGuidedTourController provides guidedTourController) {
            GuidedTourHost(
                session = guidedTourController.session,
                onNext = { coroutineScope.launch { guidedTourController.next() } },
                onPrevious = { coroutineScope.launch { guidedTourController.previous() } },
                onDontShowAgain = { coroutineScope.launch { guidedTourController.dismissForever() } },
                onDisableAllTours = { coroutineScope.launch { guidedTourController.disableAllTours() } },
                onStepRendered = { guidedTourController.markStepRendered(it) },
                modifier = Modifier.fillMaxSize(),
            ) {
                val isTv = remember { deviceDetective.isTvLikeDevice() }
                val sceneStrategy = remember(isTv) {
                    ModalBottomSheetSceneStrategy<NavKey>(isTv = isTv).then(SinglePaneSceneStrategy())
                }
                NavDisplay(
                    backStack = backStack,
                    onBack = { navCtrl.up() },
                    sceneStrategy = sceneStrategy,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    popTransitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    predictivePopTransitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider<NavKey>(
                        fallback = { unknownKey ->
                            // Safety net: render UnknownDestinationScreen instead of letting
                            // NavDisplay throw IllegalStateException if a route is ever navigated
                            // to without a matching NavigationEntry registered.
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

    // Map a shortcut/widget launch intent to its destination. Used both to seed the initial back
    // stack for a fresh launch (onCreate) and to navigate an already-live instance (onResume, for
    // onNewIntent deliveries).
    private fun shortcutRoute(intent: Intent?): NavigationDestination? =
        when (intent?.getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION)) {
            ShortcutActivity.ACTION_OPEN_APPCONTROL -> AppControlListRoute
            ShortcutActivity.ACTION_OPEN_ANALYZER -> DeviceStorageRoute
            ShortcutActivity.ACTION_UPGRADE -> UpgradeRoute()
            else -> null
        }

    private fun handleShortcutAction(intent: Intent) {
        val route = shortcutRoute(intent) ?: return
        log(TAG, VERBOSE) { "Handling shortcut action → $route" }
        navCtrl.goTo(route)
    }

    private var savedIntent: Intent? = null
    private var launchRoute: NavigationDestination? = null

    companion object {
        private val TAG = logTag("Main", "Activity")
    }
}
