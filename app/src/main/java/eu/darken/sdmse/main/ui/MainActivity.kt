package eu.darken.sdmse.main.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.recording.core.RecorderModule
import eu.darken.sdmse.common.navigation.findNavController
import eu.darken.sdmse.common.uix.Activity2
import eu.darken.sdmse.databinding.MainActivityBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()
    private lateinit var ui: MainActivityBinding
    private val navController by lazy { supportFragmentManager.findNavController(R.id.nav_host) }

    var showSplashScreen = true

    @Inject lateinit var recorderModule: RecorderModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()
        splashScreen.setKeepVisibleCondition { showSplashScreen && savedInstanceState == null }

        ui = MainActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        vm.readyState.observe2 { showSplashScreen = false }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(B_KEY_SPLASH, showSplashScreen)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val B_KEY_SPLASH = "showSplashScreen"
    }
}
