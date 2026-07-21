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
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.settings.LocalUpgradeBadgeLabel
import eu.darken.sdmse.common.compose.tour.GuidedTourHost
import eu.darken.sdmse.common.navigation.routes.AppControlListRoute
import eu.darken.sdmse.common.navigation.routes.DashboardRoute
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

        // Fresh launch: a shortcut/widget intent seeds the back stack with ONLY the target screen (see
        // Navigation()), so we never navigate as a second step. A second-step goTo() would race the
        // back-stack setup on a CLEAR_TASK recreate (the widget/launcher intents all CLEAR_TASK, and
        // MainActivity is launchMode=standard) and get discarded, landing on the Dashboard. Gating on
        // savedInstanceState avoids re-consuming the same intent after a config change. onNewIntent
        // deliveries to an already-live instance still route via onResume → handleShortcutAction.
        // Deep links are ignored while onboarding is incomplete — they must not skip consent.
        launchRoute = if (savedInstanceState == null && vm.startRoute == DashboardRoute) {
            shortcutRoute(intent)
        } else {
            null
        }

        // Widget "Clean" that opens the app (consent prompt / scan fallback). Gated on a fresh
        // start so a config change doesn't re-trigger it; the CLEAR_TASK widget intents recreate
        // MainActivity cold with a Dashboard-seeded stack, so no navigation reset is needed here.
        if (savedInstanceState == null) maybeHandleWidgetIntent(intent)

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
                    LocalUpgradeBadgeLabel provides stringResource(R.string.app_name_upgrade_postfix),
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

                    val showWidgetConsent by vm.showWidgetConsent.collectAsStateWithLifecycle()
                    if (showWidgetConsent) {
                        SdmConfirmDialog(
                            title = stringResource(R.string.widget_oneclick_consent_title),
                            message = stringResource(R.string.widget_oneclick_consent_message),
                            onDismissRequest = vm::onWidgetConsentDecline,
                            positive = SdmDialogAction(
                                label = stringResource(R.string.widget_oneclick_consent_enable_action),
                                onClick = vm::onWidgetConsentEnable,
                            ),
                            negative = SdmDialogAction(
                                label = stringResource(R.string.widget_oneclick_consent_decline_action),
                                onClick = vm::onWidgetConsentDecline,
                            ),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Navigation() {
        // A deep link seeds a ROOTLESS stack — just [launchRoute], no Dashboard underneath. With a
        // 1-entry stack Nav3 doesn't intercept the back gesture, so the system's native predictive
        // back-to-home exits in one gesture; the top-bar arrow reaches the Dashboard through the
        // controller's synthetic up-to-home fallback instead. Normal launches seed [startRoute]. On a
        // config change rememberNavBackStack restores the saved stack and ignores these args.
        val backStack = rememberNavBackStack(launchRoute ?: vm.startRoute)

        // Re-register on EVERY resume, not once: navCtrl is app-scoped, so if another MainActivity
        // instance ever stacks on top of this one (e.g. an external intent that defeats the system's
        // root-intent matching), that instance's setup() takes over the singleton. When it finishes
        // and this instance resumes, we must re-attach OUR stack — a one-shot LaunchedEffect left the
        // controller wired to the dead instance's stack, freezing all navigation (device-confirmed).
        // The home route enables up-to-Dashboard from rootless deep-link stacks; never during
        // onboarding, where a synthetic "up" would skip consent.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            navCtrl.setup(backStack, homeRoute = DashboardRoute.takeIf { vm.startRoute == DashboardRoute })
        }

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

    /**
     * Widget "Clean" fallout that lands in the app: the one-time consent prompt, or a scan whose
     * results the Dashboard already reflects (the scan was submitted by [ShortcutActivity]). Ignored
     * during onboarding — the widget must not drive the app past consent. Returns true when handled.
     */
    private fun maybeHandleWidgetIntent(intent: Intent?): Boolean {
        if (vm.startRoute != DashboardRoute) return false
        return when (intent?.getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION)) {
            ShortcutActivity.ACTION_WIDGET_CONSENT -> {
                vm.requestWidgetConsent()
                true
            }
            ShortcutActivity.ACTION_WIDGET_SCAN -> true
            else -> false
        }
    }

    private fun handleShortcutAction(intent: Intent) {
        if (maybeHandleWidgetIntent(intent)) return
        val route = shortcutRoute(intent)
        if (route == null) {
            // A plain delivery (launcher icon / widget open-app tap) onto the live singleTask
            // instance: don't resume a leftover rootless deep-link stack.
            navCtrl.resetToHomeOnPlainEntry()
            return
        }
        // Same guard as the cold-start seeding: MainActivity is exported, so a shortcut-style
        // intent could arrive via onNewIntent during onboarding — it must not skip consent.
        if (vm.startRoute != DashboardRoute) {
            log(TAG, VERBOSE) { "Ignoring shortcut action during onboarding: $route" }
            return
        }
        log(TAG, VERBOSE) { "Handling shortcut action → $route" }
        navCtrl.goTo(route)
    }

    private var savedIntent: Intent? = null
    private var launchRoute: NavigationDestination? = null

    companion object {
        private val TAG = logTag("Main", "Activity")
    }
}
