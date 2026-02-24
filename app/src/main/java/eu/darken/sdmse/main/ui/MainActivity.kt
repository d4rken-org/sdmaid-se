package eu.darken.sdmse.main.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.recorder.core.RecorderModule
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.navigation.findNavController
import eu.darken.sdmse.common.theming.Theming
import eu.darken.sdmse.common.uix.Activity2
import eu.darken.sdmse.databinding.MainActivityBinding
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.shortcuts.ShortcutManager
import eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainViewModel by viewModels()
    private lateinit var ui: MainActivityBinding

    @Suppress("unused")
    private val navController by lazy { supportFragmentManager.findNavController(R.id.nav_host) }

    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var theming: Theming
    @Inject lateinit var shortcutManager: ShortcutManager

    var showSplashScreen = true

    @Inject lateinit var recorderModule: RecorderModule

    override fun onCreate(savedInstanceState: Bundle?) {
        log(tag) { "onCreate(restoringState=${savedInstanceState != null})" }
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        theming.notifySplashScreenDone(this)
        splashScreen.setKeepOnScreenCondition { showSplashScreen && savedInstanceState == null }

        ui = MainActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        curriculumVitae.updateAppOpened()

        vm.readyState.observe2 { showSplashScreen = false }

        vm.keepScreenOn.observe2 { keepOn ->
            if (keepOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, bundle ->
            Bugs.leaveBreadCrumb("Navigated to $destination with args $bundle")
        }

        vm.errorEvents.observe2 {
            log(tag, VERBOSE) { "Error event: $it" }
            it.asErrorDialogBuilder(this).show()
        }

        handleShortcutAction(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.checkUpgrades()
        vm.checkErrors()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        log(tag, VERBOSE) { "onNewIntent() called with action: ${intent.action}" }
        handleShortcutAction(intent)
    }

    private fun handleShortcutAction(intent: Intent) {
        val shortcutAction = intent.getStringExtra(ShortcutActivity.EXTRA_SHORTCUT_ACTION)
        if (shortcutAction != null) {
            log(tag, VERBOSE) { "Handling shortcut action: $shortcutAction" }
            
            when (shortcutAction) {
                ShortcutActivity.ACTION_OPEN_APPCONTROL -> {
                    navController.navigate(
                        eu.darken.sdmse.MainDirections.goToAppControlListFragment()
                    )
                }
                ShortcutActivity.ACTION_UPGRADE -> {
                    navController.navigate(eu.darken.sdmse.MainDirections.goToUpgradeFragment())
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(B_KEY_SPLASH, showSplashScreen)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val B_KEY_SPLASH = "showSplashScreen"
    }
}
